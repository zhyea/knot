package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.mapper.ModelApiBindingMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ProviderCredentialMapper;
import org.chobit.knot.gateway.mapper.ProviderMapper;
import org.chobit.knot.gateway.service.upstream.ModelApiProtocolHandler;
import org.chobit.knot.gateway.service.upstream.ModelApiProtocolHandlerRegistry;
import org.chobit.knot.gateway.service.upstream.UpstreamProviderAdapter;
import org.chobit.knot.gateway.service.upstream.UpstreamProviderAdapterRegistry;
import org.chobit.knot.gateway.service.upstream.UpstreamRequestContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Gateway proxy facade. It builds upstream context, then delegates protocol and provider differences
 * to template-method handlers and provider strategy adapters.
 */
@Service
public class ProxyService {

    private final ProviderMapper providerMapper;
    private final ModelMapper modelMapper;
    private final ModelApiBindingMapper modelApiBindingMapper;
    private final ProviderCredentialMapper providerCredentialMapper;
    private final ModelApiProtocolHandlerRegistry protocolHandlerRegistry;
    private final UpstreamProviderAdapterRegistry providerAdapterRegistry;

    public ProxyService(ProviderMapper providerMapper,
                        ModelMapper modelMapper,
                        ModelApiBindingMapper modelApiBindingMapper,
                        ProviderCredentialMapper providerCredentialMapper,
                        ModelApiProtocolHandlerRegistry protocolHandlerRegistry,
                        UpstreamProviderAdapterRegistry providerAdapterRegistry) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
        this.modelApiBindingMapper = modelApiBindingMapper;
        this.providerCredentialMapper = providerCredentialMapper;
        this.protocolHandlerRegistry = protocolHandlerRegistry;
        this.providerAdapterRegistry = providerAdapterRegistry;
    }

    public ProxyResult proxy(Map<String, Object> requestBody, RateLimitService.AppContext appContext) {
        return proxy(requestBody, appContext, ModelApiProtocol.CHAT_COMPLETIONS);
    }

    public ProxyResult proxy(Map<String, Object> requestBody,
                             RateLimitService.AppContext appContext,
                             ModelApiProtocol protocol) {
        return proxy(requestBody, appContext, protocol, null);
    }

    public ProxyResult proxy(Map<String, Object> requestBody,
                             RateLimitService.AppContext appContext,
                             ModelApiProtocol protocol,
                             String traceparent) {
        String modelCode = requestBody.get("model") == null ? null : String.valueOf(requestBody.get("model"));
        if (modelCode == null || modelCode.isBlank()) {
            return new ProxyResult(null, null, null, "MISSING_MODEL", "model is required");
        }

        ModelEntity model = findModelByCode(modelCode);
        if (model == null) {
            return new ProxyResult(null, null, null, "MODEL_NOT_FOUND",
                    "model not found: " + modelCode);
        }

        ProviderEntity provider = providerMapper.getById(model.getProviderId());
        if (provider == null) {
            return new ProxyResult(null, null, model.getId(),
                    "PROVIDER_NOT_FOUND", "provider not found for model: " + modelCode);
        }

        ModelApiProtocol resolvedProtocol = protocol != null ? protocol.canonical() : ModelApiProtocol.CHAT_COMPLETIONS;
        ModelApiBindingEntity binding = resolveBinding(model.getId(), resolvedProtocol);
        ProviderCredentialEntity credential = providerCredentialMapper.getActiveByProviderId(provider.getId());
        UpstreamRequestContext context = new UpstreamRequestContext(
                resolvedProtocol, requestBody, model, provider, credential, binding, traceparent
        );
        ModelApiProtocolHandler protocolHandler = protocolHandlerRegistry.resolve(resolvedProtocol);
        UpstreamProviderAdapter providerAdapter = providerAdapterRegistry.resolve(provider.getProviderType());
        return protocolHandler.execute(context, providerAdapter);
    }

    private ModelApiBindingEntity resolveBinding(Long modelId, ModelApiProtocol protocol) {
        return modelApiBindingMapper.listByModelId(modelId).stream()
                .filter(item -> "ENABLED".equals(item.getStatus()))
                .filter(item -> protocol.matches(item.getProtocol()))
                .findFirst()
                .orElse(null);
    }

    private ModelEntity findModelByCode(String modelCode) {
        // TODO: add ModelMapper.getByModelCode when model lookup becomes hot path.
        return modelMapper.list(null, null).stream()
                .filter(m -> modelCode.equals(m.getModelCode()))
                .findFirst()
                .orElse(null);
    }

    public record ProxyResult(String responseBody, Long providerId, Long modelId,
                              String errorCode, String errorMessage) {
    }
}
