package org.chobit.knot.gateway.upstream;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.adapter.auth.UpstreamAuthApplier;
import org.chobit.knot.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.adapter.protocol.UpstreamAdapter;
import org.chobit.knot.adapter.model.ref.ModelApiBindingRef;
import org.chobit.knot.adapter.model.ref.ModelRef;
import org.chobit.knot.adapter.model.ref.ProviderAccountRef;
import org.chobit.knot.adapter.upstream.UpstreamAccess;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderAccountConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderCredentialConfigVo;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.plugin.PluginDispatcher;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.upstream.UpstreamPluginContext;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.svc.service.support.ProviderCredentialSupport;
import org.chobit.knot.gateway.upstream.protocol.UpstreamProtocolExecutor;
import org.chobit.knot.gateway.upstream.protocol.UpstreamProtocolExecutorRegistry;
import org.chobit.knot.gateway.upstream.request.UpstreamAdapterRegistry;
import org.chobit.knot.gateway.upstream.support.UpstreamUrlSupport;
import org.apache.commons.lang3.StringUtils;
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

    private final GatewayConfigCacheService dataService;
    private final UpstreamProtocolExecutorRegistry protocolExecutorRegistry;
    private final UpstreamAdapterRegistry requestAdapterRegistry;
    private final ProviderCredentialSupport providerCredentialSupport;
    private final PluginDispatcher pluginDispatcher;
    private final UpstreamAuthApplierCatalog authApplierCatalog;


    /**
     * Proxies the request to the upstream provider. Executes the public operation.
     *
     * @param requestId 网关自生成的请求标识（{@code GatewayRequestContext#requestId()}）：
     *                  以 {@code X-Request-Id} 头发给上游，供供应商侧检索。
     *                  <b>不是</b> {@code upstream_request_id}（那个是上游回给我们的）
     */
    public ProxyResult proxy(Map<String, Object> requestBody,
                             MediaType contentType,
                             RoutingRuleTargetDto target,
                             ModelApiProtocolEnum protocol,
                             String traceparent,
                             String requestId) {

        UpstreamRequestContext context =
                buildRequestContext(requestBody, contentType, target, protocol, traceparent, requestId);
        return proxy(context);
    }

    /**
     * Proxies the request directly against the specified provider model.
     */
    public ProxyResult proxyModel(Map<String, Object> requestBody,
                                  MediaType contentType,
                                  Long modelId,
                                  ModelApiProtocolEnum protocol,
                                  String traceparent,
                                  String requestId) {
        return proxy(buildModelRequestContext(requestBody, contentType, modelId, protocol, traceparent, requestId));
    }

    /**
     * Builds upstream request context for direct model debugging.
     */
    public UpstreamRequestContext buildModelRequestContext(Map<String, Object> requestBody,
                                                           MediaType contentType,
                                                           Long modelId,
                                                           ModelApiProtocolEnum protocol,
                                                           String traceparent,
                                                           String requestId) {
        ModelConfigVo model = resolveModel(modelId);
        ProviderAccountConfigVo provider = resolveProvider(model, model.modelCode());
        ModelApiProtocolEnum resolvedProtocol = resolveProtocol(protocol);
        ModelApiBindingConfigVo binding = resolveBinding(model.id(), resolvedProtocol);
        ProviderCredentialConfigVo credential = dataService.getActiveCredentialByProviderId(provider.id());
        return new UpstreamRequestContext(
                resolvedProtocol,
                requestBody,
                contentType,
                new ModelRef(model.id(), model.modelCode(), model.upstreamModel(),
                        model.basePath(), model.billingRuleCode(), model.modelFamilyCode()),
                new ProviderAccountRef(provider.id(), provider.code(), provider.baseUrl(),
                        provider.providerCode()),
                providerCredentialSupport.toAuthConfig(credential == null ? null : credential.encryptedConfig()),
                bindingRef(binding),
                traceparent,
                GatewayTraceContext.traceIdOf(traceparent),
                credential == null ? null : credential.authApplier(),
                requestId
        );
    }

    /**
     * Proxies a prepared upstream request context.
     */
    public ProxyResult proxy(UpstreamRequestContext context) {
        UpstreamProtocolExecutor protocolExecutor = protocolExecutorRegistry.resolve(context.protocol());
        var requestAdapter = requestAdapterRegistry.resolve(context.requestAdapter());
        String upstreamUrl = resolveUpstreamUrl(context, requestAdapter);
        dispatchPlugin(context, PluginStageCode.UPSTREAM_REQUEST, null, null, upstreamUrl);
        try {
            ProxyResult result = protocolExecutor.execute(context, requestAdapter);
            dispatchPlugin(context, PluginStageCode.UPSTREAM_RESPONSE, result, null, upstreamUrl);
            return result;
        } catch (RuntimeException ex) {
            dispatchPlugin(context, PluginStageCode.UPSTREAM_ERROR, null, ex, upstreamUrl);
            throw ex;
        }
    }

    private UpstreamRequestContext buildRequestContext(Map<String, Object> requestBody,
                                                       MediaType contentType,
                                                       RoutingRuleTargetDto target,
                                                       ModelApiProtocolEnum protocol,
                                                       String traceparent,
                                                       String requestId) {
        ModelConfigVo model = resolveModel(target);
        ProviderAccountConfigVo provider = resolveProvider(model, target == null ? null : target.targetCode());
        ModelApiProtocolEnum resolvedProtocol = resolveProtocol(protocol);
        ModelApiBindingConfigVo binding = resolveBinding(model.id(), resolvedProtocol);
        ProviderCredentialConfigVo credential = dataService.getActiveCredentialByProviderId(provider.id());
        return new UpstreamRequestContext(
                resolvedProtocol,
                requestBody,
                contentType,
                new ModelRef(model.id(), model.modelCode(), model.upstreamModel(),
                        model.basePath(), model.billingRuleCode(), model.modelFamilyCode()),
                new ProviderAccountRef(provider.id(), provider.code(), provider.baseUrl(),
                        provider.providerCode()),
                providerCredentialSupport.toAuthConfig(credential == null ? null : credential.encryptedConfig()),
                bindingRef(binding),
                traceparent,
                GatewayTraceContext.traceIdOf(traceparent),
                credential == null ? null : credential.authApplier(),
                requestId
        );
    }

    /**
     * 绑定投影：模型未绑该协议时实体为 null（{@code resolveBinding} 取不到即回落协议默认路径），
     * 投影保持 null，由 {@link UpstreamRequestContext#requestAdapter()} 等消费方判空。
     */
    private static ModelApiBindingRef bindingRef(ModelApiBindingConfigVo binding) {
        return binding == null ? null : new ModelApiBindingRef(
                binding.apiPath(), binding.requestAdapter(), binding.protocol(),
                binding.usageExtractor(), binding.streamUsageExtractor());
    }

    private ModelConfigVo resolveModel(RoutingRuleTargetDto target) {
        return resolveModel(target == null ? null : target.targetId(), target == null ? null : target.targetCode());
    }

    private ModelConfigVo resolveModel(Long modelId) {
        return resolveModel(modelId, null);
    }

    private ModelConfigVo resolveModel(Long modelId, String modelCode) {
        ModelConfigVo model = modelId == null ? null : dataService.getModelById(modelId);
        if (model == null) {
            throw new GatewayUpstreamException("model not found: " + modelCode, ProxyErrorCodeEnum.MODEL_NOT_FOUND.code());
        }
        return model;
    }

    private ProviderAccountConfigVo resolveProvider(ModelConfigVo model, String modelCode) {
        ProviderAccountConfigVo provider = dataService.getProviderAccountByCode(model.providerAccountCode());
        if (provider == null) {
            throw new GatewayUpstreamException("provider not found for model: " + modelCode, ProxyErrorCodeEnum.PROVIDER_NOT_FOUND.code());
        }
        return provider;
    }

    private ModelApiProtocolEnum resolveProtocol(ModelApiProtocolEnum protocol) {
        return protocol != null ? protocol.canonical() : ModelApiProtocolEnum.CHAT_COMPLETIONS;
    }

    private ModelApiBindingConfigVo resolveBinding(Long modelId, ModelApiProtocolEnum protocol) {
        return dataService.listApiBindingsByModelId(modelId).stream()
                .filter(item -> EnabledStatusEnum.isEnabled(item.status()))
                .filter(item -> protocol.matches(item.protocol()))
                .findFirst()
                .orElse(null);
    }

    private void dispatchPlugin(UpstreamRequestContext context,
                                PluginStageCode stageCode,
                                ProxyResult result,
                                Throwable error,
                                String upstreamUrl) {
        pluginDispatcher.dispatch(
                PluginExtensionPoint.UPSTREAM_EXCHANGE,
                stageCode,
                new UpstreamPluginContext(
                        context.traceId(),
                        context.traceparent(),
                        stageCode,
                        context.provider() == null ? null : context.provider().id(),
                        context.provider() == null ? null : context.provider().code(),
                        context.model() == null ? null : context.model().id(),
                        context.model() == null ? null : context.model().modelCode(),
                        context.protocol() == null ? null : context.protocol().code(),
                        context.binding() == null ? null : context.binding().protocol(),
                        context.requestBody(),
                        context.contentType(),
                        responseSnapshot(result),
                        error,
                        upstreamRequestIdOf(result, error),
                        upstreamUrl
                )
        );
    }

    /**
     * 解析本次上游调用的完整请求地址（scheme + host + path）：与执行器使用相同的
     * {@code joinPath} / {@code joinUrl} 原语，在请求发起前即可算出，供三个阶段的日志统一呈现。
     */
    private String resolveUpstreamUrl(UpstreamRequestContext context, UpstreamAdapter adapter) {
        String protocolPath = adapter.resolvePath(context, context.protocol().defaultPath());
        UpstreamAuthApplier applier = authApplierCatalog.resolve(context.authApplier());
        UpstreamAccess access = applier == null ? null : applier.prepare(context);
        String baseUrl = access == null ? null : StringUtils.trimToNull(access.baseUrl());
        String path = UpstreamUrlSupport.joinPath(
                context.model() == null ? null : context.model().basePath(), protocolPath);
        return UpstreamUrlSupport.joinUrl(baseUrl, path);
    }

    /**
     * 供应商侧请求 id：成功取自 {@link ProxyResult}，失败取自
     * {@link GatewayUpstreamException}（两路都由 executor 从上游响应头归一）；
     * UPSTREAM_REQUEST 阶段（result 与 error 均空）或非上游异常时为 null。
     */
    private String upstreamRequestIdOf(ProxyResult result, Throwable error) {
        if (result != null) {
            return result.upstreamRequestId();
        }
        return error instanceof GatewayUpstreamException upstream ? upstream.upstreamRequestId() : null;
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
}
