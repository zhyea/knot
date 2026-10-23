package org.chobit.knot.gateway.upstream.protocol;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.auth.UpstreamAuthApplier;
import org.chobit.knot.gateway.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamAccess;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.adapter.upstream.UpstreamSignableRequest;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.upstream.support.UpstreamRequestIdResolver;
import org.chobit.knot.gateway.upstream.support.UpstreamUrlSupport;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Map;

/**
 * Executes the public operation. Executes the public operation.
 *
 * <p>2026-10-20：上游客户端由 {@code RestClient}（Apache HttpClient 5）迁到
 * {@link WebClient}（reactor-netty）。两条路径的<b>阻塞边界不变</b>——仍然阻塞到「响应头就绪」
 * 才返回，这样首字节之前的上游失败依旧能被 failover 捕获。</p>
 */
public abstract class AbstractUpstreamProtocolExecutor implements UpstreamProtocolExecutor {

    private final WebClient webClient;
    private final UsageExtractorRegistry usageExtractorRegistry;
    private final UpstreamAuthApplierCatalog authApplierCatalog;
    /**
     * 上游客户端配置：取 {@code responseTimeout} 作为「到响应头就绪」的总超时，
     * 取 {@code streamBufferSize} 作为流式桥接管道容量。
     */
    private final GatewayUpstreamClientProperties clientProperties;

    protected AbstractUpstreamProtocolExecutor(WebClient webClient,
                                               UsageExtractorRegistry usageExtractorRegistry,
                                               UpstreamAuthApplierCatalog authApplierCatalog,
                                               GatewayUpstreamClientProperties clientProperties) {
        this.webClient = webClient;
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.authApplierCatalog = authApplierCatalog;
        this.clientProperties = clientProperties;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public ProxyResult execute(UpstreamRequestContext context, UpstreamRequestAdapter adapter) {
        String protocolPath = adapter.resolvePath(context, defaultPath(context));
        if (StringUtils.isBlank(protocolPath)) {
            throw new GatewayUpstreamException(
                    "api protocol is not configured: " + context.protocol().code(),
                    ProxyErrorCodeEnum.API_PROTOCOL_NOT_CONFIGURED.code()
            );
        }
        // 接入策略每请求解析并准备一次：base URL 与静态鉴权头同源，构造、拼接与签名共用同一结果
        UpstreamAuthApplier applier = authApplierCatalog.resolve(context.authApplier());
        UpstreamAccess access = applier.prepare(context);
        String baseUrl = StringUtils.trimToNull(access.baseUrl());
        if (baseUrl == null) {
            // 端点配置错误必须在请求前失败：放行 null 会拼出相对 URI，错误信息与真实原因脱节
            throw new GatewayUpstreamException(
                    "upstream endpoint is not configured: providerAccount=" + providerAccountCode(context),
                    ProxyErrorCodeEnum.UPSTREAM_ENDPOINT_NOT_CONFIGURED.code()
            );
        }
        // 模型级 base_path 只做 URL 前缀，由执行器统一拼接；适配器只负责协议路径
        String path = UpstreamUrlSupport.joinPath(
                context.model() == null ? null : context.model().getBasePath(), protocolPath);
        RequestTarget target = new RequestTarget(
                baseUrl, path, adapter.resolveContentType(context), access, applier);
        try {
            // 适配器可自行关掉流式（AWS Bedrock 的流式是 EventStream 二进制帧，P1 走整包）
            if (supportsStreaming(context) && adapter.streamSupported(context) && isStreamRequest(context)) {
                return executeStreaming(context, adapter, target);
            }
            return executeBuffered(context, adapter, target);
        } catch (GatewayUpstreamException e) {
            throw e;
        } catch (WebClientResponseException e) {
            // 兜底：漏网的非 2xx（如 onStatus 未覆盖的重定向）也要带齐上游上下文
            throw upstreamFailure(e.getStatusCode(), e.getHeaders(), e.getResponseBodyAsString(),
                    context.requestId(), target.url());
        } catch (Exception e) {
            throw new GatewayUpstreamException(e.getMessage(), ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
    }

    /**
     * 单次上游请求的目标。
     *
     * <p>URL 只在这里算一次，请求构造与签名共用同一个字符串——签名覆盖的路径与
     * 实际发出的路径必须逐字符一致（SigV4 的第二雷区）。</p>
     *
     * @param baseUrl     接入策略产出的生效 base URL
     * @param path        已拼上模型 base_path 的最终路径
     * @param contentType 请求内容类型
     * @param access      接入策略的完整结果（含静态鉴权头）
     * @param applier     解析出的接入策略，签名阶段复用，避免重复 resolve
     */
    private record RequestTarget(String baseUrl,
                                 String path,
                                 MediaType contentType,
                                 UpstreamAccess access,
                                 UpstreamAuthApplier applier) {

        String url() {
            return UpstreamUrlSupport.joinUrl(baseUrl, path);
        }
    }

    private static String providerAccountCode(UpstreamRequestContext context) {
        return context.provider() == null ? "<none>" : context.provider().getCode();
    }

    /**
     * 整包缓冲：把上游响应完整读成字符串后返回。适用于非流式请求。
     *
     * <p>非 2xx 读掉错误体后抛 {@link GatewayUpstreamException}（带头），与流式路径同构。</p>
     */
    private ProxyResult executeBuffered(UpstreamRequestContext context,
                                        UpstreamRequestAdapter adapter,
                                        RequestTarget target) {
        String upstreamUrl = target.url();
        Mono<BufferedExchange> exchange = withBody(context, adapter, target)
                .exchangeToMono(response -> readBuffered(response, context.requestId(), upstreamUrl));
        BufferedExchange buffered = withResponseTimeout(exchange).block();
        String handledResponseBody = adapter.handleResponse(buffered.body(), context);
        return new ProxyResult(
                handledResponseBody,
                context.provider().getId(),
                context.model().getId(),
                usageExtractorRegistry.extract(handledResponseBody, context, adapter),
                null,
                buffered.upstreamRequestId(),
                upstreamUrl
        );
    }

    /**
     * 读整包响应：响应体字符串 + 上游响应头白名单快照（归一 request-id）。
     *
     * <p>非 2xx 读掉错误体后抛 {@link GatewayUpstreamException}：失败场景的供应商 id
     * 恰恰是排障工单里最关键的检索键（详见设计文档 §4.2 捕获点三）。</p>
     *
     * <p>返回 {@link Mono} 而不是直接读：本方法运行在 reactor-netty 的 event loop 线程上，
     * 在这里 {@code block()} 会把 event loop 卡死。</p>
     *
     * <p>包级可见供单测直接驱动（{@code ClientResponse.create(...)}），
     * 不经端到端 WebClient——那要拖 UsageExtractorRegistry 整条依赖链。</p>
     *
     * @param ownRequestId 我方本次请求的 request_id：用于排除上游把我方
     *                     {@code X-Request-Id} 回显回来的情况，防止它冒充
     *                     {@code upstream_request_id}（两者是不同系统的编号）
     */
    static Mono<BufferedExchange> readBuffered(ClientResponse response, String ownRequestId, String upstreamUrl) {
        HttpStatusCode statusCode = response.statusCode();
        HttpHeaders headers = response.headers().asHttpHeaders();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    if (!statusCode.is2xxSuccessful()) {
                        throw upstreamFailure(statusCode, headers, body, ownRequestId, upstreamUrl);
                    }
                    return new BufferedExchange(body, UpstreamRequestIdResolver.requestId(headers, ownRequestId));
                });
    }

    /**
     * 整包交换结果：响应体 + 供应商侧请求标识（白名单归一，无命中为 null）。
     */
    record BufferedExchange(String body, String upstreamRequestId) {
    }

    /**
     * 流式转发：只阻塞等到响应头就绪，响应体交给 {@link UpstreamStreamResponse} 边收边发。
     *
     * <p>首字节之前的状态码错误、连接超时、读超时都会在这里抛出，
     * 因此 failover 仍然有效；一旦开始转发则无法再切换目标。</p>
     *
     * <p>{@code toEntityFlux} 是这里唯一可用的入口：{@code exchangeToMono} 会在转换函数
     * 返回后调用 {@code releaseBody()}，把尚未消费的响应体整个吞掉。</p>
     */
    private ProxyResult executeStreaming(UpstreamRequestContext context,
                                        UpstreamRequestAdapter adapter,
                                        RequestTarget target) {
        Mono<ResponseEntity<Flux<DataBuffer>>> response = withBody(context, adapter, target)
                .retrieve()
                                            .onStatus(status -> !status.is2xxSuccessful(),
                        failed -> upstreamFailureOf(failed, context.requestId(), target.url()))
                .toEntityFlux(DataBuffer.class);
        ResponseEntity<Flux<DataBuffer>> entity = withResponseTimeout(response).block();
        HttpHeaders headers = entity.getHeaders();
        // 响应体 Flux 原样带出（批2：不再用管道桥接成 InputStream），订阅即转发、取消即归还连接
        UpstreamStreamResponse streamResponse = new UpstreamStreamResponse(
                entity.getBody(),
                headers.getContentType(),
                context,
                adapter,
                UpstreamRequestIdResolver.snapshot(headers, context.requestId()),
                UpstreamRequestIdResolver.requestId(headers, context.requestId()));
        return new ProxyResult(
                null,
                context.provider().getId(),
                context.model().getId(),
                null,
                streamResponse,
                streamResponse.upstreamRequestId(),
                target.url()
        );
    }

    /**
     * 非 2xx 的统一映射：读掉错误体后抛出带齐上游上下文（状态码 / 错误体 / Retry-After / 供应商 id）的异常。
     *
     * @param ownRequestId 我方 request_id，用于排除上游回显值（见 {@link UpstreamRequestIdResolver}）
     */
    private Mono<Throwable> upstreamFailureOf(ClientResponse response, String ownRequestId, String upstreamUrl) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> upstreamFailure(
                        response.statusCode(), response.headers().asHttpHeaders(), body, ownRequestId, upstreamUrl));
    }

    private static GatewayUpstreamException upstreamFailure(HttpStatusCode statusCode,
                                                           HttpHeaders headers,
                                                           String body,
                                                           String ownRequestId,
                                                           String upstreamUrl) {
        return new GatewayUpstreamException(
                "upstream responded with status " + statusCode.value(),
                ProxyErrorCodeEnum.UPSTREAM_ERROR.code(),
                statusCode.value(),
                body,
                retryAfterMs(headers),
                UpstreamRequestIdResolver.requestId(headers, ownRequestId),
                upstreamUrl
        );
    }

    /**
     * 读取上游 {@code Retry-After} 响应头，换算成毫秒供重试退避参考。
     *
     * <p>只有 429/503 这类明确限流响应才会携带；拿不到就返回 null，
     * 退避退化为纯指数退避，不影响主流程。</p>
     */
    private static Long retryAfterMs(HttpHeaders headers) {
        return headers == null ? null : parseRetryAfter(headers.getFirst("Retry-After"));
    }

    /**
     * 解析 {@code Retry-After}：优先按「秒数」解释，其次按 HTTP 日期（RFC 1123）解释。
     *
     * <p>两种都解析不了返回 null。秒数为负或日期已过统一按 0 处理（立即重试由退避策略决定），
     * 并做上界收敛避免异常大的值溢出。</p>
     */
    static Long parseRetryAfter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return Math.max(0L, Math.min(seconds, 3_600L) * 1_000L);
        } catch (NumberFormatException ignored) {
            // 不是秒数，继续按 HTTP 日期解释
        }
        try {
            ZonedDateTime moment = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Math.max(0L, moment.toInstant().toEpochMilli() - System.currentTimeMillis());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * 总响应超时：只卡「到响应头就绪」这一段，长流式响应不会被整体误杀
     * （响应体由 {@code ProxyStreamingFlux} 惰性读取，受 readTimeout 静默超时约束）。
     */
    private <T> Mono<T> withResponseTimeout(Mono<T> source) {
        Duration timeout = clientProperties.getResponseTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return source;
        }
        return source.timeout(timeout);
    }

    /**
     * 当前协议是否支持流式转发。默认关闭，由文本生成类协议打开。
     */
    protected boolean supportsStreaming(UpstreamRequestContext context) {
        return false;
    }

    private boolean isStreamRequest(UpstreamRequestContext context) {
        Map<String, Object> body = context.requestBody();
        return body != null && Boolean.TRUE.equals(body.get(AiPayloadFields.STREAM));
    }

    /**
     * 请求规格：URI + 内容类型 + 追踪头 + 接入策略的静态鉴权头。
     *
     * @param encodedUri 为 {@code true} 时走 {@code uri(URI)}——Bedrock 的路径里含已编码的
     *                   {@code %3A}（模型 ID 的 {@code :}），走 {@code uri(String)} 会被
     *                   URI 模板解析再编一次，导致「签名覆盖的路径」与「实际发出的路径」不一致
     */
    private WebClient.RequestBodySpec requestSpec(UpstreamRequestContext context,
                                                 RequestTarget target,
                                                 boolean encodedUri) {
        String url = target.url();
        WebClient.RequestBodySpec spec = (encodedUri
                ? webClient.post().uri(URI.create(url))
                : webClient.post().uri(url))
                .contentType(target.contentType())
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM);
        if (StringUtils.isNotBlank(context.traceparent())) {
            spec.header(GatewayHeaders.TRACEPARENT, StringUtils.trim(context.traceparent()));
        }
        // 网关自生成的请求标识：上游日志按它可检索到我方编号（与响应侧采集的
        // upstream_request_id 是两个不同的 id，见 GatewayHeaders#X_REQUEST_ID）
        String requestId = StringUtils.trimToNull(context.requestId());
        if (requestId != null) {
            spec.header(GatewayHeaders.X_REQUEST_ID, requestId);
        }
        // 静态鉴权头来自接入策略的 prepare 结果，与 base URL 同源（签名头在签名阶段追加）
        if (!target.access().headers().isEmpty()) {
            spec.headers(current -> current.addAll(target.access().headers()));
        }
        return spec;
    }

    /**
     * 装好请求体之后的可执行请求：multipart 走 {@code BodyInserters.fromMultipartData}，
     * 其余按值序列化；空 body 显式落成空插入器（{@code bodyValue} 不接受 null）。
     */
    @SuppressWarnings("unchecked")
    private WebClient.RequestHeadersSpec<?> withBody(UpstreamRequestContext context,
                                                     UpstreamRequestAdapter adapter,
                                                     RequestTarget target) {
        Object body = adapter.buildRequestBody(context);
        if (body == null) {
            return requestSpec(context, target, false).body(BodyInserters.empty());
        }
        if (MediaType.MULTIPART_FORM_DATA.includes(target.contentType()) && body instanceof Map<?, ?> map) {
            // multipart 不进签名路径（Bedrock 无 multipart 接口），保持原编码分支不变
            WebClient.RequestBodySpec spec = requestSpec(context, target, false);
            return spec.body(BodyInserters.fromMultipartData(toMultipartBody((Map<String, Object>) map)));
        }
        if (!target.applier().requiresFinalizedPayload()) {
            return requestSpec(context, target, false).bodyValue(body);
        }
        // 签名路径：先把请求体定稿成字节，签名覆盖这份字节，再以同一份字节发出（字节级一致 = 第一雷区）
        String json = JsonKit.toJson(body);
        byte[] payload = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        WebClient.RequestBodySpec spec = requestSpec(context, target, true);
        HttpHeaders signed = new HttpHeaders();
        target.applier().sign(UpstreamSignableRequest.of("POST", target.url(), payload), signed, context);
        if (!signed.isEmpty()) {
            spec.headers(current -> current.addAll(signed));
        }
        return spec.body(BodyInserters.fromValue(payload));
    }

    protected String defaultPath(UpstreamRequestContext context) {
        return context.protocol().defaultPath();
    }

    /**
     * 组装 multipart 请求体。
     *
     * <p>2026-10-20（WebFlux 批2）：入参从 {@code MultipartFile}（Servlet 类型）换成
     * {@link FilePart}。文件部分走 {@code asyncPart}——直接把上游收到的
     * {@code Flux<DataBuffer>} 转给上游，<b>不落内存也不落盘</b>，
     * 与旧实现里「读完整包再当 Resource 发」相比省掉一次全量复制。</p>
     */
    private MultiValueMap<String, HttpEntity<?>> toMultipartBody(Map<String, Object> source) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        source.forEach((key, value) -> addMultipartPart(builder, key, value));
        return builder.build();
    }

    private void addMultipartPart(MultipartBodyBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof FilePart filePart) {
            addFilePart(builder, key, filePart);
            return;
        }
        // 多文件 part（如 /images/edits 的 image）由控制器以 List<FilePart> 形态下发；
        // 不用 FilePart[] —— Spring 的 @RequestPart 解析器不支持数组形态（详见
        // GatewayImageController#edits 的 javadoc），当年 Servlet 栈的 MultipartFile[]
        // 由 MVC 专用 resolver 兜着，切 WebFlux 后没有对应分支
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addMultipartPart(builder, key, item));
            return;
        }
        builder.part(key, value);
    }

    private void addFilePart(MultipartBodyBuilder builder, String key, FilePart filePart) {
        if (filePart == null) {
            return;
        }
        MultipartBodyBuilder.PartBuilder part =
                builder.asyncPart(key, filePart.content(), DataBuffer.class);
        if (filePart.filename() != null) {
            part.filename(filePart.filename());
        }
        MediaType contentType = filePart.headers().getContentType();
        if (contentType != null) {
            part.contentType(contentType);
        }
    }
}
