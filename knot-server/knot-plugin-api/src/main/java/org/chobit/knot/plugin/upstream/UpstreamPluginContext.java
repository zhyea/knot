package org.chobit.knot.plugin.upstream;

import org.chobit.knot.plugin.PluginDispatchContext;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.PluginBodySourceEnum;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

public class UpstreamPluginContext extends PluginDispatchContext {

    private final PluginStageCode stageCode;
    private final Long providerId;
    private final String providerCode;
    private final Long modelId;
    private final String modelCode;
    private final String protocol;
    private final String bindingProtocol;
    private final Map<String, Object> requestBody;
    private final MediaType contentType;
    private final Map<String, Object> responseSnapshot;
    private final Throwable error;
    /**
     * 供应商侧请求 id（上游响应头白名单归一）：UPSTREAM_REQUEST 阶段恒为 null，
     * UPSTREAM_RESPONSE / UPSTREAM_ERROR 阶段有值；无命中为 null。供日志插件输出，
     * 用于网关与供应商联合排查，不透客户端。
     */
    private final String upstreamRequestId;
    /**
     * 本次上游调用的完整请求地址（scheme + host + path）：UPSTREAM_REQUEST 阶段（请求发起前已可算出）
     * 与 UPSTREAM_RESPONSE / UPSTREAM_ERROR 阶段同值，供日志呈现「请求打到了哪个供应商端点」。
     */
    private final String upstreamUrl;
    /**
     * 上游 HTTP 状态码：仅 UPSTREAM_ERROR 阶段且失败来自上游响应时有值；超时 / 连接失败等
     * 未拿到响应的失败为 null。
     */
    private final Integer httpStatus;
    /**
     * 上游错误响应体原文：仅 UPSTREAM_ERROR 阶段且上游给了响应体时有值；其余为 null。
     * 与成功路径 {@code responseSnapshot.responseBody}（{@code adapter.handleResponse} 适配结果）语义不同，本字段是供应商 wire 原文。
     */
    private final String upstreamResponseBody;
    /**
     * 本次快照里正文所处的层次：整包响应是适配后的，流式补记与异步任务查询是供应商原文。
     * 由分发侧给出，插件不猜；请求 / 错误阶段没有响应快照，为 {@code null}。
     */
    private final PluginBodySourceEnum bodySource;

    public UpstreamPluginContext(String traceId,
                                 String traceparent,
                                 PluginStageCode stageCode,
                                 Long providerId,
                                 String providerCode,
                                 Long modelId,
                                 String modelCode,
                                 String protocol,
                                 String bindingProtocol,
                                 Map<String, Object> requestBody,
                                 MediaType contentType,
                                 Map<String, Object> responseSnapshot,
                                 Throwable error,
                                 String upstreamUrl) {
        this(traceId, traceparent, stageCode, providerId, providerCode, modelId, modelCode,
                protocol, bindingProtocol, requestBody, contentType, responseSnapshot, error,
                null, upstreamUrl, null, null);
    }

    public UpstreamPluginContext(String traceId,
                                 String traceparent,
                                 PluginStageCode stageCode,
                                 Long providerId,
                                 String providerCode,
                                 Long modelId,
                                 String modelCode,
                                 String protocol,
                                 String bindingProtocol,
                                 Map<String, Object> requestBody,
                                 MediaType contentType,
                                 Map<String, Object> responseSnapshot,
                                 Throwable error,
                                 String upstreamRequestId,
                                 String upstreamUrl) {
        this(traceId, traceparent, stageCode, providerId, providerCode, modelId, modelCode,
                protocol, bindingProtocol, requestBody, contentType, responseSnapshot, error,
                upstreamRequestId, upstreamUrl, null, null);
    }

    /**
     * 完整构造：额外带上「上游错误证据」（HTTP 状态码 + 错误体原文）。
     */
    public UpstreamPluginContext(String traceId,
                                 String traceparent,
                                 PluginStageCode stageCode,
                                 Long providerId,
                                 String providerCode,
                                 Long modelId,
                                 String modelCode,
                                 String protocol,
                                 String bindingProtocol,
                                 Map<String, Object> requestBody,
                                 MediaType contentType,
                                 Map<String, Object> responseSnapshot,
                                 Throwable error,
                                 String upstreamRequestId,
                                 String upstreamUrl,
                                 Integer httpStatus,
                                 String upstreamResponseBody) {
        this(traceId, traceparent, stageCode, providerId, providerCode, modelId, modelCode,
                protocol, bindingProtocol, requestBody, contentType, responseSnapshot, error,
                upstreamRequestId, upstreamUrl, httpStatus, upstreamResponseBody, null);
    }

    /**
     * 完整构造：在「上游错误证据」之上再带正文来源标记，供日志插件如实标注。
     */
    public UpstreamPluginContext(String traceId,
                                 String traceparent,
                                 PluginStageCode stageCode,
                                 Long providerId,
                                 String providerCode,
                                 Long modelId,
                                 String modelCode,
                                 String protocol,
                                 String bindingProtocol,
                                 Map<String, Object> requestBody,
                                 MediaType contentType,
                                 Map<String, Object> responseSnapshot,
                                 Throwable error,
                                 String upstreamRequestId,
                                 String upstreamUrl,
                                 Integer httpStatus,
                                 String upstreamResponseBody,
                                 PluginBodySourceEnum bodySource) {
        super(traceId, traceparent);
        this.stageCode = stageCode;
        this.providerId = providerId;
        this.providerCode = providerCode;
        this.modelId = modelId;
        this.modelCode = modelCode;
        this.protocol = protocol;
        this.bindingProtocol = bindingProtocol;
        this.requestBody = requestBody == null ? Map.of() : new LinkedHashMap<>(requestBody);
        this.contentType = contentType;
        this.responseSnapshot = responseSnapshot == null ? Map.of() : new LinkedHashMap<>(responseSnapshot);
        this.error = error;
        this.upstreamRequestId = upstreamRequestId;
        this.upstreamUrl = upstreamUrl;
        this.httpStatus = httpStatus;
        this.upstreamResponseBody = upstreamResponseBody;
        this.bodySource = bodySource;
    }

    public PluginStageCode stageCode() {
        return stageCode;
    }

    public Long providerId() {
        return providerId;
    }

    public String providerCode() {
        return providerCode;
    }

    public Long modelId() {
        return modelId;
    }

    public String modelCode() {
        return modelCode;
    }

    public String protocol() {
        return protocol;
    }

    public String bindingProtocol() {
        return bindingProtocol;
    }

    public Map<String, Object> requestBody() {
        return requestBody;
    }

    public MediaType contentType() {
        return contentType;
    }

    public Map<String, Object> responseSnapshot() {
        return responseSnapshot;
    }

    public Throwable error() {
        return error;
    }

    /**
     * 供应商侧请求 id（上游响应头白名单归一）；UPSTREAM_REQUEST 阶段或无命中为 null。
     */
    public String upstreamRequestId() {
        return upstreamRequestId;
    }

    public String upstreamUrl() {
        return upstreamUrl;
    }

    /**
     * 上游 HTTP 状态码；仅 UPSTREAM_ERROR 且失败来自上游响应时有值，否则 null。
     */
    public Integer httpStatus() {
        return httpStatus;
    }

    /**
     * 上游错误响应体原文；仅 UPSTREAM_ERROR 且上游给了响应体时有值，否则 null。
     */
    public String upstreamResponseBody() {
        return upstreamResponseBody;
    }

    /**
     * 快照正文所处层次；无响应快照（请求 / 错误阶段）时为 {@code null}。
     */
    public PluginBodySourceEnum bodySource() {
        return bodySource;
    }
}
