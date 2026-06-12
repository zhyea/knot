package org.chobit.knot.gateway.adapter.upstream;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

public record UpstreamRequestContext(ModelApiProtocolEnum protocol,
                                     Map<String, Object> requestBody,
                                     MediaType contentType,
                                     ModelEntity model,
                                     ProviderEntity provider,
                                     Map<String, Object> authConfig,
                                     ModelApiBindingEntity binding,
                                     String traceparent,
                                     String traceId) {

    public UpstreamRequestContext(ModelApiProtocolEnum protocol,
                                  Map<String, Object> requestBody,
                                  MediaType contentType,
                                  ModelEntity model,
                                  ProviderEntity provider,
                                  Map<String, Object> authConfig,
                                  ModelApiBindingEntity binding,
                                  String traceparent,
                                  String traceId) {
        this.protocol = protocol;
        this.requestBody = requestBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestBody);
        this.contentType = contentType == null ? MediaType.APPLICATION_JSON : contentType;
        this.model = model;
        this.provider = provider;
        this.authConfig = authConfig == null ? Map.of() : Map.copyOf(authConfig);
        this.binding = binding;
        this.traceparent = traceparent;
        this.traceId = traceId;
    }

    public String baseUrl() {
        return model == null ? null : model.getBaseUrl();
    }

    public String requestAdapter() {
        return binding == null ? null : binding.getRequestAdapter();
    }
}
