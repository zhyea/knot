package org.chobit.knot.gateway.controller;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.GatewayBadRequestException;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.exception.GatewayRequestException;
import org.chobit.knot.gateway.exception.GatewayTrafficRejectException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.GatewayErrorResponse;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.gateway.redis.RedisUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Slf4j
@ControllerAdvice(assignableTypes = GatewayControllerSupport.class)
public class GatewayExceptionHandler {

    /** 上游响应摘要长度上限：够放下 OpenRouter 的 {"error":{...}}，也不会把 HTML 错误页整页回透 */
    private static final int UPSTREAM_BODY_LIMIT = 300;

    /**
     * Handles missing required gateway headers.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public GatewayErrorResponse handleMissingRequestHeader(MissingRequestHeaderException e) {
        return GatewayErrorResponse.of(
                "Missing required header: " + e.getHeaderName(),
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code()
        );
    }

    /**
     * Handles malformed gateway requests.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public GatewayErrorResponse handleInvalidRequest(Exception e) {
        log.warn("Invalid gateway request", e);
        return GatewayErrorResponse.of(
                "Invalid request body",
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code()
        );
    }

    /**
     * Handles gateway authentication errors.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(GatewayAuthException.class)
    public GatewayErrorResponse handleGatewayAuthException(GatewayAuthException e) {
        log.warn("Gateway authentication exception: message={}", e.getMessage());
        return gatewayError(e);
    }

    /**
     * Handles gateway request validation errors.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(GatewayInvalidRequestException.class)
    public GatewayErrorResponse handleGatewayInvalidRequestException(GatewayInvalidRequestException e) {
        log.warn("Gateway request exception: message={}", e.getMessage());
        return gatewayError(e);
    }

    /**
     * Handles cross-module 400 rejections (e.g. sensitive word moderation).
     *
     * <p>400 rather than 429: 429 is reserved for rate limit and quota. Only the matched
     * category code is exposed — never the word itself, to prevent dictionary probing.</p>
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(GatewayBadRequestException.class)
    public GatewayErrorResponse handleGatewayBadRequestException(GatewayBadRequestException e) {
        log.warn("Gateway bad request: type={}, code={}, message={}", e.errorType(), e.code(), e.getMessage());
        return GatewayErrorResponse.of(e.getMessage(), e.errorType(), e.code());
    }

    /**
     * Handles gateway rate limit and quota errors.
     *
     * <p>Both land on 429: callers need only one retry strategy, and the response body
     * carries a structured {@code detail} (resource, dimension, limit, used, reset time)
     * plus a {@code Retry-After} header telling when the window resets.</p>
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(GatewayTrafficRejectException.class)
    public GatewayErrorResponse handleGatewayTrafficRejectException(GatewayTrafficRejectException e,
                                                                    HttpServletResponse response) {
        log.warn("Gateway traffic rejected: type={}, code={}, message={}", e.type(), e.code(), e.getMessage());
        TrafficRejectDetail detail = e.detail();
        if (detail != null && detail.resetAfterSeconds() > 0L) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(detail.resetAfterSeconds()));
        }
        return GatewayErrorResponse.ofRejection(e.getMessage(), e.type(), e.code(), detail);
    }

    /**
     * Handles upstream gateway errors.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(GatewayUpstreamException.class)
    public GatewayErrorResponse handleGatewayUpstreamException(GatewayUpstreamException e) {
        log.warn("Gateway upstream exception: code={}, upstreamStatus={}, message={}",
                e.code(), e.httpStatus(), e.getMessage());
        // HTTP 状态码保持 502（错误码隔离），但把上游原状态码透进响应体，避免语义丢失
        return GatewayErrorResponse.ofUpstream(e.getMessage(), e.type(), e.code(),
                e.httpStatus(), truncateUpstreamBody(e.responseBody()));
    }

    /** 上游响应体可能很大（HTML 错误页等），只留诊断所需的短摘要 */
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
     * <p>这里打ERROR 是<b>刻意</b>的：ERROR 级日志是本项目的报警通道，
     * 日志采集侧按 {@code Redis unavailable} 关键字配规则。{@link RedisCache} 已有
     * 分钟级节流防刷屏，此处再打一次是为了覆盖非存储入口（如管理器扫描）的故障现场。</p>
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(RedisUnavailableException.class)
    public GatewayErrorResponse handleRedisUnavailable(RedisUnavailableException e) {
        log.error("Gateway storage unavailable: domain={}, operation={}, key={}",
                e.getDomain(), e.getOperation(), e.getKey(), e);
        return GatewayErrorResponse.of(
                "Gateway shared storage unavailable, please retry later",
                GatewayErrorTypeEnum.STORAGE_UNAVAILABLE.code()
        );
    }

    /**
     * Handles unexpected business errors from gateway dependencies.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BusinessException.class)
    public GatewayErrorResponse handleBusinessException(BusinessException e) {
        log.warn("Gateway business exception: code={}, message={}", e.getCode(), e.getMessage());
        return GatewayErrorResponse.of(
                e.getMessage(),
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code(),
                e.getCode()
        );
    }

    /**
     * Handles unexpected gateway failures.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public GatewayErrorResponse handleException(Exception e) {
        log.error("Unexpected gateway error", e);
        return GatewayErrorResponse.of(
                "Internal server error",
                GatewayErrorTypeEnum.UPSTREAM_ERROR.code()
        );
    }

    private static GatewayErrorResponse gatewayError(GatewayRequestException e) {
        return GatewayErrorResponse.of(e.getMessage(), e.type(), e.code());
    }
}
