package org.chobit.knot.gateway.upstream;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.plugin.PluginDispatcher;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.service.ProviderCredentialSupport;
import org.chobit.knot.gateway.upstream.protocol.UpstreamProtocolExecutor;
import org.chobit.knot.gateway.upstream.protocol.UpstreamProtocolExecutorRegistry;
import org.chobit.knot.gateway.upstream.request.UpstreamRequestAdapterRegistry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
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
    private final UpstreamRequestAdapterRegistry requestAdapterRegistry;
    private final ProviderCredentialSupport providerCredentialSupport;
    private final PluginDispatcher pluginDispatcher;


    /**
     * Proxies the request to the upstream provider. Executes the public operation.
     */
    public ProxyResult proxy(Map<String, Object> requestBody,
                             MediaType contentType,
                             RoutingRuleTargetDto target,
                             ModelApiProtocolEnum protocol,
                             String traceparent) {

        UpstreamRequestContext context = buildRequestContext(requestBody, contentType, target, protocol, traceparent);
        return proxy(context);
    }

    /**
     * Proxies the request directly against the specified provider model.
     */
    public ProxyResult proxyModel(Map<String, Object> requestBody,
                                  MediaType contentType,
                                  Long modelId,
                                  ModelApiProtocolEnum protocol,
                                  String traceparent) {
        return proxy(buildModelRequestContext(requestBody, contentType, modelId, protocol, traceparent));
    }

    /**
     * Builds upstream request context for direct model debugging.
     */
    public UpstreamRequestContext buildModelRequestContext(Map<String, Object> requestBody,
                                                           MediaType contentType,
                                                           Long modelId,
                                                           ModelApiProtocolEnum protocol,
                                                           String traceparent) {
        ModelEntity model = resolveModel(modelId);
        ProviderEntity provider = resolveProvider(model, model.getModelCode());
        ModelApiProtocolEnum resolvedProtocol = resolveProtocol(protocol);
        ModelApiBindingEntity binding = resolveBinding(model.getId(), resolvedProtocol);
        return new UpstreamRequestContext(
                resolvedProtocol,
                requestBody,
                contentType,
                model,
                provider,
                providerCredentialSupport.toAuthConfig(dataService.getActiveCredentialByProviderId(provider.getId())),
                binding,
                traceparent,
                currentTraceId()
        );
    }

    /**
     * Proxies a prepared upstream request context.
     */
    public ProxyResult proxy(UpstreamRequestContext context) {
        UpstreamProtocolExecutor protocolExecutor = protocolExecutorRegistry.resolve(context.protocol());
        var requestAdapter = requestAdapterRegistry.resolve(
                context.requestAdapter(),
                context.provider() == null ? null : context.provider().getProviderType()
        );
        dispatchPlugin(context, PluginStageCode.UPSTREAM_REQUEST, null, null);
        try {
            ProxyResult result = protocolExecutor.execute(context, requestAdapter);
            dispatchPlugin(context, PluginStageCode.UPSTREAM_RESPONSE, result, null);
            return result;
        } catch (RuntimeException ex) {
            dispatchPlugin(context, PluginStageCode.UPSTREAM_ERROR, null, ex);
            throw ex;
        }
    }

    private UpstreamRequestContext buildRequestContext(Map<String, Object> requestBody,
                                                       MediaType contentType,
                                                       RoutingRuleTargetDto target,
                                                       ModelApiProtocolEnum protocol,
                                                       String traceparent) {
        ModelEntity model = resolveModel(target);
        ProviderEntity provider = resolveProvider(model, target == null ? null : target.targetCode());
        ModelApiProtocolEnum resolvedProtocol = resolveProtocol(protocol);
        ModelApiBindingEntity binding = resolveBinding(model.getId(), resolvedProtocol);
        return new UpstreamRequestContext(
                resolvedProtocol,
                requestBody,
                contentType,
                model,
                provider,
                providerCredentialSupport.toAuthConfig(dataService.getActiveCredentialByProviderId(provider.getId())),
                binding,
                traceparent,
                currentTraceId()
        );
    }

    private ModelEntity resolveModel(RoutingRuleTargetDto target) {
        return resolveModel(target == null ? null : target.targetId(), target == null ? null : target.targetCode());
    }

    private ModelEntity resolveModel(Long modelId) {
        return resolveModel(modelId, null);
    }

    private ModelEntity resolveModel(Long modelId, String modelCode) {
        ModelEntity model = modelId == null ? null : dataService.getModelById(modelId);
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

    private void dispatchPlugin(UpstreamRequestContext context,
                                PluginStageCode stageCode,
                                ProxyResult result,
                                Throwable error) {
        pluginDispatcher.dispatch(
                PluginExtensionPoint.UPSTREAM_EXCHANGE,
                stageCode,
                new UpstreamPluginContext(
                        context.traceId(),
                        context.traceparent(),
                        stageCode,
                        context.provider() == null ? null : context.provider().getId(),
                        context.provider() == null ? null : context.provider().getCode(),
                        context.model() == null ? null : context.model().getId(),
                        context.model() == null ? null : context.model().getModelCode(),
                        context.protocol() == null ? null : context.protocol().code(),
                        context.binding() == null ? null : context.binding().getProtocol(),
                        context.requestBody(),
                        context.contentType(),
                        responseSnapshot(result),
                        error
                )
        );
    }

    private Map<String, Object> responseSnapshot(ProxyResult result) {
        if (result == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("providerId", result.providerId());
        snapshot.put("modelId", result.modelId());
        snapshot.put("responseBody", result.responseBody());
        snapshot.put("usage", result.usage());
        return snapshot;
    }

    private String currentTraceId() {
        GatewayTraceContext current = GatewayTraceContext.currentOrCreate(null);
        return current == null ? null : current.traceId();
    }
}
