package org.chobit.knot.gateway.controller;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.GatewayBadRequestException;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.exception.GatewayRequestException;
import org.chobit.knot.gateway.exception.GatewayTrafficRejectException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.GatewayErrorDescriptor;
import org.chobit.knot.gateway.model.GatewayErrorResponse;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.gateway.redis.RedisUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.concurrent.TimeoutException;

/**
 * 网关统一异常出口：把链路上的异常翻译成 OpenAI 兼容的错误响应。
 *
 * <p>2026-10-20（WebFlux 批2）：
 * <ul>
 *   <li>{@code @ControllerAdvice} + {@code @ResponseBody} + {@code @ResponseStatus}
 *       改为 {@code @RestControllerAdvice} + {@link ResponseEntity}——Netty 栈没有
 *       {@code HttpServletResponse} 可以就地改头，{@code Retry-After} 只能走响应实体；</li>
 *   <li>{@code AsyncRequestTimeoutException}（Servlet 异步超时）换成
 *       {@link TimeoutException}（Reactor 的 {@code Mono.timeout}）；</li>
 *   <li>补 {@link ServerWebInputException}：WebFlux 把「请求体解析失败 / 缺 @RequestParam」
 *       统一表达成它，Servlet 时代的 {@code HttpMessageNotReadableException} 不再出现。</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestControllerAdvice(assignableTypes = GatewayControllerSupport.class)
public class GatewayExceptionHandler {

    /**
     * 上游响应摘要长度上限：够放下 OpenRouter 的 {"error":{...}}，也不会把 HTML 错误页整页回透
     */
    private static final int UPSTREAM_BODY_LIMIT = 300;

    /**
     * Handles missing required gateway headers / unreadable request bodies.
     *
     * <p>2026-10-20（WebFlux 批2）：<b>不能再按 {@code MissingRequestHeaderException} 分支</b>——
     * 该异常的父类链是 {@code MissingRequestValueException → ServletRequestBindingException →
     * jakarta.servlet.ServletException}，Netty 栈上没有 Servlet API，一加载就
     * {@code NoClassDefFoundError}（本类所有方法在 bean 后处理阶段被反射扫描，整类都建不起来）。
     * WebFlux 缺头时抛的是 {@link ServerWebInputException}，原因文本里已带上头名。</p>
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            ServerWebInputException.class})
    public ResponseEntity<GatewayErrorResponse> handleInvalidRequest(Exception e) {
        String reason = e instanceof ServerWebInputException input && input.getReason() != null
                ? input.getReason()
                : "Invalid request body";
        log.warn("Invalid gateway request: {}", reason);
        return status(HttpStatus.BAD_REQUEST, GatewayErrorResponse.of(
                reason,
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code()
        ));
    }

    /**
     * Handles gateway authentication errors.
     */
    @ExceptionHandler(GatewayAuthException.class)
    public ResponseEntity<GatewayErrorResponse> handleGatewayAuthException(GatewayAuthException e) {
        log.warn("Gateway authentication exception: message={}", e.getMessage());
        return status(e, gatewayError(e));
    }

    /**
     * Handles gateway request validation errors.
     */
    @ExceptionHandler(GatewayInvalidRequestException.class)
    public ResponseEntity<GatewayErrorResponse> handleGatewayInvalidRequestException(GatewayInvalidRequestException e) {
        log.warn("Gateway request exception: message={}", e.getMessage());
        return status(e, gatewayError(e));
    }

    /**
     * Handles cross-module 400 rejections (e.g. sensitive word moderation).
     *
     * <p>400 rather than 429: 429 is reserved for rate limit and quota. Only the matched
     * category code is exposed — never the word itself, to prevent dictionary probing.</p>
     */
    @ExceptionHandler(GatewayBadRequestException.class)
    public ResponseEntity<GatewayErrorResponse> handleGatewayBadRequestException(GatewayBadRequestException e) {
        log.warn("Gateway bad request: type={}, code={}, message={}", e.errorType(), e.code(), e.getMessage());
        GatewayErrorDescriptor descriptor = GatewayErrorDescriptor.of(e);
        return status(descriptor, GatewayErrorResponse.of(e.getMessage(), descriptor.type(), descriptor.code()));
    }

    /**
     * Handles gateway rate limit and quota errors.
     *
     * <p>Both land on 429: callers need only one retry strategy, and the response body
     * carries a structured {@code detail} (resource, dimension, limit, used, reset time)
     * plus a {@code Retry-After} header telling when the window resets.</p>
     */
    @ExceptionHandler(GatewayTrafficRejectException.class)
    public ResponseEntity<GatewayErrorResponse> handleGatewayTrafficRejectException(GatewayTrafficRejectException e) {
        log.warn("Gateway traffic rejected: type={}, code={}, message={}", e.type(), e.code(), e.getMessage());
        TrafficRejectDetail detail = e.detail();
        GatewayErrorDescriptor descriptor = GatewayErrorDescriptor.of(e);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(descriptor.httpStatus());
        if (detail != null && detail.resetAfterSeconds() > 0L) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(detail.resetAfterSeconds()));
        }
        return builder.body(GatewayErrorResponse.ofRejection(e.getMessage(), descriptor.type(), descriptor.code(), detail));
    }

    /**
     * Handles upstream gateway errors.
     */
    @ExceptionHandler(GatewayUpstreamException.class)
    public ResponseEntity<GatewayErrorResponse> handleGatewayUpstreamException(GatewayUpstreamException e) {
        log.warn("Gateway upstream exception: code={}, upstreamStatus={}, message={}",
                e.code(), e.httpStatus(), e.getMessage());
        // HTTP 状态码保持 502（错误码隔离），但把上游原状态码透进响应体，避免语义丢失
        GatewayErrorDescriptor descriptor = GatewayErrorDescriptor.of(e);
        return status(descriptor, GatewayErrorResponse.ofUpstream(e.getMessage(), descriptor.type(), descriptor.code(),
                e.httpStatus(), truncateUpstreamBody(e.responseBody())));
    }

    /**
     * 上游响应体可能很大（HTML 错误页等），只留诊断所需的短摘要
     */
    private static String truncateUpstreamBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String flat = body.trim();
        return flat.length() <= UPSTREAM_BODY_LIMIT ? flat : flat.substring(0, UPSTREAM_BODY_LIMIT) + "…";
    }

    /**
     * 共享状态存储（Redis）不可用：返回 503 + {@code storage_unavailable}。
     *
     * <p>刻意<b>不</b>复用 {@link GatewayUpstreamException}：上游不可用是「模型服务的问题」，
     * 存储不可用是「网关自己的 Redis 挂了」，两者处置完全不同（一个可换节点/重试，
     * 一个必须先修 Redis），日志告警也要分别配规则。</p>
     *
     * <p>这里打 ERROR 是<b>刻意</b>的：ERROR 级日志是本项目的报警通道，
     * 日志采集侧按 {@code Redis unavailable} 关键字配规则。{@code RedisCache} 已有
     * 分钟级节流防刷屏，此处再打一次是为了覆盖非存储入口（如管理器扫描）的故障现场。</p>
     */
    @ExceptionHandler(RedisUnavailableException.class)
    public ResponseEntity<GatewayErrorResponse> handleRedisUnavailable(RedisUnavailableException e) {
        log.error("Gateway storage unavailable: domain={}, operation={}, key={}",
                e.getDomain(), e.getOperation(), e.getKey(), e);
        GatewayErrorDescriptor descriptor = GatewayErrorDescriptor.of(e);
        return status(descriptor, GatewayErrorResponse.of(
                "Gateway shared storage unavailable, please retry later", descriptor.type()));
    }

    /**
     * Handles unexpected business errors from gateway dependencies.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GatewayErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("Gateway business exception: code={}, message={}", e.getCode(), e.getMessage());
        GatewayErrorDescriptor descriptor = GatewayErrorDescriptor.of(e);
        return status(descriptor, GatewayErrorResponse.of(e.getMessage(), descriptor.type(), descriptor.code()));
    }

    /**
     * Handles unexpected gateway failures.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayErrorResponse> handleException(Exception e) {
        log.error("Unexpected gateway error", e);
        GatewayErrorDescriptor descriptor = GatewayErrorDescriptor.of(e);
        return status(descriptor, GatewayErrorResponse.of("Internal server error", descriptor.type()));
    }

    /**
     * 全局请求预算超时（B2 deadline）：请求在 {@code knot.gateway.upstream.request-budget}
     * 内未就绪即被 {@code Mono.timeout} 终止，返回 504。
     *
     * <p>2026-10-20（WebFlux 批2）：触发源由 {@code DeferredResult} 的超时换成
     * {@code reactor} 的 {@link TimeoutException}，语义不变——这是「唯一真总超时」的出口。
     * 与 {@link #handleGatewayUpstreamException}（上游真的回了错误或挂死）区分：
     * 本项表示「网关自己设定的总时限到了」，错误码 {@code timeout_error} 便于排查与限流策略区分。</p>
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<GatewayErrorResponse> handleRequestBudgetExceeded(TimeoutException e) {
        log.warn("Gateway request budget exceeded: {}", e.getMessage());
        GatewayErrorDescriptor descriptor = GatewayErrorDescriptor.of(e);
        return status(descriptor, GatewayErrorResponse.of(
                "Gateway request timeout, please retry later", descriptor.type()));
    }

    /** HTTP 状态码取自 {@link GatewayErrorDescriptor}，与数仓出口同一映射，不许在出口侧另写死值 */
    private static ResponseEntity<GatewayErrorResponse> status(GatewayErrorDescriptor descriptor,
                                                               GatewayErrorResponse body) {
        return ResponseEntity.status(descriptor.httpStatus()).body(body);
    }

    /**
     * Spring 框架异常专用：它们只出现在 HTTP 入口、不进数仓，没有对应的
     * {@link GatewayErrorDescriptor} 映射，状态码只能在出口侧直接给。
     */
    private static ResponseEntity<GatewayErrorResponse> status(HttpStatus status, GatewayErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }

    /** 异常实例重载：家族异常的 type / code 在构造时已取自枚举，此处直接透传 */
    private static ResponseEntity<GatewayErrorResponse> status(Throwable error, GatewayErrorResponse body) {
        return ResponseEntity.status(GatewayErrorDescriptor.of(error).httpStatus()).body(body);
    }

    private static GatewayErrorResponse gatewayError(GatewayRequestException e) {
        return GatewayErrorResponse.of(e.getMessage(), e.type(), e.code());
    }
}
