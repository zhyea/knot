package org.chobit.knot.plugin.gateway;

import org.chobit.knot.common.moderation.ModerationTerm;
import org.chobit.knot.plugin.PluginDispatchContext;
import org.chobit.knot.plugin.PluginStageCode;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GatewayPluginContext extends PluginDispatchContext {

    private final PluginStageCode stageCode;
    private final String apiKey;
    private final String ruleCode;
    private final String protocol;
    private final GatewayRoutingSnapshot routingSnapshot;
    private final List<ModerationTerm> words;
    private final Map<String, Object> requestBody;
    private final MediaType contentType;
    private final Object responseBody;
    private final Throwable error;

    public GatewayPluginContext(String traceId,
                                String traceparent,
                                PluginStageCode stageCode,
                                String apiKey,
                                String ruleCode,
                                String protocol,
                                GatewayRoutingSnapshot routingSnapshot,
                                List<ModerationTerm> words,
                                Map<String, Object> requestBody,
                                MediaType contentType,
                                Object responseBody,
                                Throwable error) {
        super(traceId, traceparent);
        this.stageCode = stageCode;
        this.apiKey = apiKey;
        this.ruleCode = ruleCode;
        this.protocol = protocol;
        this.routingSnapshot = routingSnapshot;
        this.words = words;
        this.requestBody = requestBody == null ? Map.of() : new LinkedHashMap<>(requestBody);
        this.contentType = contentType;
        this.responseBody = responseBody;
        this.error = error;
    }

    public PluginStageCode stageCode() {
        return stageCode;
    }

    public String apiKey() {
        return apiKey;
    }

    public String ruleCode() {
        return ruleCode;
    }

    public String protocol() {
        return protocol;
    }

    public GatewayRoutingSnapshot routingSnapshot() {
        return routingSnapshot;
    }

    /**
     * 全量启用词条（中性数据）：网关每请求经上下文下发的最新词库资源，守卫编译自动机时使用。
     * 词库不可用时为空列表（fail-open）。编译与复用是 knot-plugin 内部实现，不进契约层。
     */
    public List<ModerationTerm> words() {
        return words;
    }

    public Map<String, Object> requestBody() {
        return requestBody;
    }

    public MediaType contentType() {
        return contentType;
    }

    public Object responseBody() {
        return responseBody;
    }

    public Throwable error() {
        return error;
    }
}
