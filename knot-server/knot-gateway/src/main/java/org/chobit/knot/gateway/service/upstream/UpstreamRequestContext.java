package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public class UpstreamRequestContext {

    private final ModelApiProtocol protocol;
    private final Map<String, Object> requestBody;
    private final ModelEntity model;
    private final ProviderEntity provider;
    private final ProviderCredentialEntity credential;
    private final ModelApiBindingEntity binding;
    private final String traceparent;

    public UpstreamRequestContext(ModelApiProtocol protocol,
                                  Map<String, Object> requestBody,
                                  ModelEntity model,
                                  ProviderEntity provider,
                                  ProviderCredentialEntity credential,
                                  ModelApiBindingEntity binding,
                                  String traceparent) {
        this.protocol = protocol;
        this.requestBody = new LinkedHashMap<>(requestBody);
        this.model = model;
        this.provider = provider;
        this.credential = credential;
        this.binding = binding;
        this.traceparent = traceparent;
    }

    public ModelApiProtocol protocol() {
        return protocol;
    }

    public Map<String, Object> requestBody() {
        return requestBody;
    }

    public ModelEntity model() {
        return model;
    }

    public ProviderEntity provider() {
        return provider;
    }

    public ProviderCredentialEntity credential() {
        return credential;
    }

    public ModelApiBindingEntity binding() {
        return binding;
    }

    public String traceparent() {
        return traceparent;
    }
}
