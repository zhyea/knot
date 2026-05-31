package org.chobit.knot.gateway.upstream;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.model.AppContext;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.upstream.ModelApiProtocolHandler;
import org.chobit.knot.gateway.upstream.ModelApiProtocolHandlerRegistry;
import org.chobit.knot.gateway.upstream.UpstreamProviderAdapter;
import org.chobit.knot.gateway.upstream.UpstreamProviderAdapterRegistry;
import org.chobit.knot.gateway.upstream.UpstreamRequestContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Gateway proxy facade. It builds upstream context, then delegates protocol and provider differences
 * to template-method handlers and provider strategy adapters.
 */
@Component
@RequiredArgsConstructor
public class UpstreamProxyClient {

    private final GatewayDataService dataService;
    private final ModelApiProtocolHandlerRegistry protocolHandlerRegistry;
    private final UpstreamProviderAdapterRegistry providerAdapterRegistry;

    /**
     * Proxies the request to the upstream provider. Executes the public operation.
     */
    public ProxyResult proxy(Map<String, Object> requestBody, AppContext appContext) {
        return proxy(requestBody, appContext, ModelApiProtocolEnum.CHAT_COMPLETIONS);
    }

    /**
     * Proxies the request to the upstream provider. Executes the public operation.
     */
    public ProxyResult proxy(Map<String, Object> requestBody,
                             AppContext appContext,
                             ModelApiProtocolEnum protocol) {
        return proxy(requestBody, appContext, protocol, null);
    }

    /**
     * Proxies the request to the upstream provider. Executes the public operation.
     */
    public ProxyResult proxy(Map<String, Object> requestBody,
                             AppContext appContext,
                             ModelApiProtocolEnum protocol,
                             String traceparent) {
        String modelCode = requestBody.get(AiPayloadFields.MODEL) == null
                ? null
                : String.valueOf(requestBody.get(AiPayloadFields.MODEL));
        if (modelCode == null || modelCode.isBlank()) {
            return new ProxyResult(null, null, null, ProxyErrorCodeEnum.MISSING_MODEL.code(), "model is required");
        }

        ModelEntity model = findModelByCode(modelCode);
        if (model == null) {
            return new ProxyResult(null, null, null, ProxyErrorCodeEnum.MODEL_NOT_FOUND.code(),
                    "model not found: " + modelCode);
        }

        ProviderEntity provider = dataService.getProviderById(model.getProviderId());
        if (provider == null) {
            return new ProxyResult(null, null, model.getId(),
                    ProxyErrorCodeEnum.PROVIDER_NOT_FOUND.code(), "provider not found for model: " + modelCode);
        }

        ModelApiProtocolEnum resolvedProtocol = protocol != null ? protocol.canonical() : ModelApiProtocolEnum.CHAT_COMPLETIONS;
        ModelApiBindingEntity binding = resolveBinding(model.getId(), resolvedProtocol);
        ProviderCredentialEntity credential = dataService.getActiveCredentialByProviderId(provider.getId());
        UpstreamRequestContext context = new UpstreamRequestContext(
                resolvedProtocol, requestBody, model, provider, credential, binding, traceparent
        );
        ModelApiProtocolHandler protocolHandler = protocolHandlerRegistry.resolve(resolvedProtocol);
        UpstreamProviderAdapter providerAdapter = providerAdapterRegistry.resolve(provider.getProviderType());
        return protocolHandler.execute(context, providerAdapter);
    }

    private ModelApiBindingEntity resolveBinding(Long modelId, ModelApiProtocolEnum protocol) {
        return dataService.listApiBindingsByModelId(modelId).stream()
                .filter(item -> EntityStatusEnum.ENABLED.code().equals(item.getStatus()))
                .filter(item -> protocol.matches(item.getProtocol()))
                .findFirst()
                .orElse(null);
    }

    private ModelEntity findModelByCode(String modelCode) {
        return dataService.getModelByCode(modelCode);
    }
}
