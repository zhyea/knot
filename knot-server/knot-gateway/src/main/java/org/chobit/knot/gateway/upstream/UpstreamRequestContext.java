package org.chobit.knot.gateway.upstream;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

public record UpstreamRequestContext(ModelApiProtocolEnum protocol,
                                     Map<String, Object> requestBody,
                                     MediaType contentType,
                                     ModelEntity model,
                                     ProviderEntity provider,
                                     ProviderCredentialEntity credential,
                                     ModelApiBindingEntity binding,
                                     String traceparent,
                                     String traceId) {

    /**
     * Constructs a new instance.
     */
    public UpstreamRequestContext(ModelApiProtocolEnum protocol,
                                  Map<String, Object> requestBody,
                                  MediaType contentType,
                                  ModelEntity model,
                                  ProviderEntity provider,
                                  ProviderCredentialEntity credential,
                                  ModelApiBindingEntity binding,
                                  String traceparent,
                                  String traceId) {
        this.protocol = protocol;
        this.requestBody = new LinkedHashMap<>(requestBody);
        this.contentType = contentType == null ? MediaType.APPLICATION_JSON : contentType;
        this.model = model;
        this.provider = provider;
        this.credential = credential;
        this.binding = binding;
        this.traceparent = traceparent;
        this.traceId = traceId;
    }
}
