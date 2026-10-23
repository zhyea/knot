package org.chobit.knot.gateway.adapter.upstream;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

public record UpstreamRequestContext(ModelApiProtocolEnum protocol,
                                     Map<String, Object> requestBody,
                                     MediaType contentType,
                                     ModelEntity model,
                                     ProviderAccountEntity provider,
                                     Map<String, Object> authConfig,
                                     ModelApiBindingEntity binding,
                                     String traceparent,
                                     String traceId,
                                     String authApplier,
                                     String requestId) {

    /** 便利构造：鉴权策略与请求标识默认 null（前者回退 Bearer，后者不出站发 {@code X-Request-Id}）。 */
    public UpstreamRequestContext(ModelApiProtocolEnum protocol,
                                  Map<String, Object> requestBody,
                                  MediaType contentType,
                                  ModelEntity model,
                                  ProviderAccountEntity provider,
                                  Map<String, Object> authConfig,
                                  ModelApiBindingEntity binding,
                                  String traceparent,
                                  String traceId) {
        this(protocol, requestBody, contentType, model, provider, authConfig, binding,
                traceparent, traceId, null, null);
    }

    public UpstreamRequestContext(ModelApiProtocolEnum protocol,
                                  Map<String, Object> requestBody,
                                  MediaType contentType,
                                  ModelEntity model,
                                  ProviderAccountEntity provider,
                                  Map<String, Object> authConfig,
                                  ModelApiBindingEntity binding,
                                  String traceparent,
                                  String traceId,
                                  String authApplier,
                                  String requestId) {
        this.protocol = protocol;
        this.requestBody = requestBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestBody);
        this.contentType = contentType == null ? MediaType.APPLICATION_JSON : contentType;
        this.model = model;
        this.provider = provider;
        this.authConfig = authConfig == null ? Map.of() : Map.copyOf(authConfig);
        this.binding = binding;
        this.traceparent = traceparent;
        this.traceId = traceId;
        this.authApplier = authApplier;
        this.requestId = requestId;
    }

    public String requestAdapter() {
        return binding == null ? null : binding.getRequestAdapter();
    }
}
