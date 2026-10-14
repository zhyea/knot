package org.chobit.knot.gateway.plugin.upstream;

import org.chobit.knot.gateway.plugin.PluginDispatchContext;
import org.chobit.knot.gateway.plugin.PluginStageCode;
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
                                 Throwable error) {
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
}
