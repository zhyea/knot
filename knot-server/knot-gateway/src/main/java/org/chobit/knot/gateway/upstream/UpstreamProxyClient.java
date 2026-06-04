package org.chobit.knot.gateway.upstream;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.upstream.protocol.UpstreamProtocolExecutor;
import org.chobit.knot.gateway.upstream.protocol.UpstreamProtocolExecutorRegistry;
import org.chobit.knot.gateway.upstream.provider.UpstreamProviderAdapter;
import org.chobit.knot.gateway.upstream.provider.UpstreamProviderAdapterRegistry;
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
    private final UpstreamProtocolExecutorRegistry protocolExecutorRegistry;
    private final UpstreamProviderAdapterRegistry providerAdapterRegistry;


    /**
     * Proxies the request to the upstream provider. Executes the public operation.
     */
    public ProxyResult proxy(Map<String, Object> requestBody,
                             RoutingRuleTargetDto target,
                             ModelApiProtocolEnum protocol,
                             String traceparent) {

        UpstreamRequestContext context = buildRequestContext(requestBody, target, protocol, traceparent);
        UpstreamProtocolExecutor protocolExecutor = protocolExecutorRegistry.resolve(context.protocol());
        UpstreamProviderAdapter providerAdapter = providerAdapterRegistry.resolve(context.provider().getProviderType());

        return protocolExecutor.execute(context, providerAdapter);
    }

    private UpstreamRequestContext buildRequestContext(Map<String, Object> requestBody,
                                                       RoutingRuleTargetDto target,
                                                       ModelApiProtocolEnum protocol,
                                                       String traceparent) {
        ModelEntity model = resolveModel(target);
        ProviderEntity provider = resolveProvider(model, target);
        ModelApiProtocolEnum resolvedProtocol = resolveProtocol(protocol);
        ModelApiBindingEntity binding = resolveBinding(model.getId(), resolvedProtocol);
        ProviderCredentialEntity credential = dataService.getActiveCredentialByProviderId(provider.getId());
        return new UpstreamRequestContext(
                resolvedProtocol, requestBody, model, provider, credential, binding, traceparent
        );
    }

    private ModelEntity resolveModel(RoutingRuleTargetDto target) {
        ModelEntity model = target == null ? null : dataService.getModelById(target.targetId());
        if (model == null) {
            String targetCode = target == null ? null : target.targetCode();
            throw new GatewayUpstreamException("model not found: " + targetCode, ProxyErrorCodeEnum.MODEL_NOT_FOUND.code());
        }
        return model;
    }

    private ProviderEntity resolveProvider(ModelEntity model, RoutingRuleTargetDto target) {
        ProviderEntity provider = dataService.getProviderById(model.getProviderId());
        if (provider == null) {
            throw new GatewayUpstreamException("provider not found for model: " + target.targetCode(), ProxyErrorCodeEnum.PROVIDER_NOT_FOUND.code());
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
}
