package org.chobit.knot.gateway.plugin.gateway;

import org.chobit.knot.gateway.plugin.PluginDispatchContext;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

public class GatewayPluginContext extends PluginDispatchContext {

    private final PluginStageCode stageCode;
    private final String apiKey;
    private final String ruleCode;
    private final String protocol;
    private final Map<String, Object> routingSnapshot;
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
                                Map<String, Object> routingSnapshot,
                                Map<String, Object> requestBody,
                                MediaType contentType,
                                Object responseBody,
                                Throwable error) {
        super(traceId, traceparent);
        this.stageCode = stageCode;
        this.apiKey = apiKey;
        this.ruleCode = ruleCode;
        this.protocol = protocol;
        this.routingSnapshot = routingSnapshot == null ? Map.of() : new LinkedHashMap<>(routingSnapshot);
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

    public Map<String, Object> routingSnapshot() {
        return routingSnapshot;
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
