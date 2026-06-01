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
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.service.GatewayDataService;
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
    public ProxyResult proxy(Map<String, Object> requestBody,
                             ModelApiProtocolEnum protocol,
                             String traceparent) {

        UpstreamRequestContext context = buildRequestContext(requestBody, protocol, traceparent);
        ModelApiProtocolHandler protocolHandler = protocolHandlerRegistry.resolve(context.protocol());
        UpstreamProviderAdapter providerAdapter = providerAdapterRegistry.resolve(context.provider().getProviderType());
        return protocolHandler.execute(context, providerAdapter);
    }

    private UpstreamRequestContext buildRequestContext(Map<String, Object> requestBody,
                                                       ModelApiProtocolEnum protocol,
                                                       String traceparent) {
        String modelCode = extractModelCode(requestBody);
        ModelEntity model = resolveModel(modelCode);
        ProviderEntity provider = resolveProvider(model, modelCode);
        ModelApiProtocolEnum resolvedProtocol = resolveProtocol(protocol);
        ModelApiBindingEntity binding = resolveBinding(model.getId(), resolvedProtocol);
        ProviderCredentialEntity credential = dataService.getActiveCredentialByProviderId(provider.getId());
        return new UpstreamRequestContext(
                resolvedProtocol, requestBody, model, provider, credential, binding, traceparent
        );
    }

    private String extractModelCode(Map<String, Object> requestBody) {
        String modelCode = requestBody.get(AiPayloadFields.MODEL) == null
                ? null
                : String.valueOf(requestBody.get(AiPayloadFields.MODEL));
        if (modelCode == null || modelCode.isBlank()) {
            throw new GatewayUpstreamException("model is required", ProxyErrorCodeEnum.MISSING_MODEL.code());
        }
        return modelCode;
    }

    private ModelEntity resolveModel(String modelCode) {
        ModelEntity model = findModelByCode(modelCode);
        if (model == null) {
            throw new GatewayUpstreamException("model not found: " + modelCode, ProxyErrorCodeEnum.MODEL_NOT_FOUND.code());
        }
        return model;
    }

    private ProviderEntity resolveProvider(ModelEntity model, String modelCode) {
        ProviderEntity provider = dataService.getProviderById(model.getProviderId());
        if (provider == null) {
            throw new GatewayUpstreamException("provider not found for model: " + modelCode, ProxyErrorCodeEnum.PROVIDER_NOT_FOUND.code());
        }
        return provider;
    }

    private ModelApiProtocolEnum resolveProtocol(ModelApiProtocolEnum protocol) {
        return protocol != null ? protocol.canonical() : ModelApiProtocolEnum.CHAT_COMPLETIONS;
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
