package org.chobit.knot.gateway.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.adapter.auth.UpstreamAuthApplier;
import org.chobit.knot.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.adapter.model.ref.ModelRef;
import org.chobit.knot.adapter.model.ref.ProviderAccountRef;
import org.chobit.knot.adapter.upstream.UpstreamAccess;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderAccountConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderCredentialConfigVo;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.gateway.upstream.support.UpstreamUrlSupport;
import org.chobit.knot.plugin.PluginDispatcher;
import org.chobit.knot.svc.service.support.ProviderCredentialSupport;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * 异步任务查询客户端：按档案拼查询请求，取回终态响应体原文。
 * 只把「查上游」做成一次普通的 GET（或档案声明的其它方法），响应体原文交给提取/计费管线，不解析业务字段。
 * 鉴权与 URL 复用 {@code UpstreamAuthApplier} + {@code UpstreamUrlSupport}；超时取 {@link AsyncTaskProperties#getQueryTimeoutMs()}，启动期由 {@code AsyncTaskPoller#init} 校验「查询超时 < 占位有效期」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskQueryClient implements AsyncTaskQueryPort {

    private final WebClient webClient;
    private final AsyncTaskProperties properties;
    private final GatewayConfigCacheService dataService;
    private final ProviderCredentialSupport credentialSupport;
    private final UpstreamAuthApplierCatalog authApplierCatalog;
    private final PluginDispatcher pluginDispatcher;

    /**
     * 查询一次任务状态。
     *
     * @return 响应体原文；查询失败抛异常，由调用方按退避处理（不在这里吞）
     */

    @Override
    public String query(AsyncTaskContext context,
                        AsyncTaskSpec spec,
                        String upstreamTaskId) {
        ModelConfigVo model = dataService.getModelByCode(context.modelCode());
        if (model == null) {
            throw new IllegalStateException("async task model not found: " + context.modelCode());
        }
        ProviderAccountConfigVo provider = dataService.getProviderAccountByCode(model.providerAccountCode());
        if (provider == null) {
            throw new IllegalStateException("async task provider not found for model: " + context.modelCode());
        }
        ProviderCredentialConfigVo credential = dataService.getActiveCredentialByProviderId(provider.id());
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode(context.protocol());
        UpstreamRequestContext upstreamContext = new UpstreamRequestContext(
                protocol,
                Map.of(),
                MediaType.APPLICATION_JSON,
                new ModelRef(model.id(), model.modelCode(), model.upstreamModel(),
                        model.basePath(), model.billingRuleCode(), model.modelFamilyCode()),
                new ProviderAccountRef(provider.id(), provider.code(), provider.baseUrl(),
                        provider.providerCode()),
                credentialSupport.toAuthConfig(credential == null ? null : credential.encryptedConfig()),
                null,
                context.traceparent(),
                GatewayTraceContext.traceIdOf(context.traceparent()),
                credential == null ? null : credential.authApplier(),
                context.requestId());
        UpstreamAuthApplier applier = authApplierCatalog.resolve(upstreamContext.authApplier());
        UpstreamAccess access = applier == null ? null : applier.prepare(upstreamContext);
        if (access == null || access.baseUrl() == null) {
            throw new IllegalStateException("async task upstream endpoint not configured: " + context.modelCode());
        }
        String path = spec == null ? null : spec.resolvePath(upstreamTaskId);
        String url = UpstreamUrlSupport.joinUrl(access.baseUrl(),
                UpstreamUrlSupport.joinPath(model.basePath(), path));
        return exchangeWithPlugins(upstreamContext, url, context.taskCode(), access,
                spec == null ? HttpMethod.GET : HttpMethod.valueOf(spec.queryMethod().toUpperCase()));
    }

    private String exchangeWithPlugins(UpstreamRequestContext upstreamContext,
                                       String url,
                                       String taskCode,
                                       UpstreamAccess access,
                                       HttpMethod method) {
        return AsyncTaskQueryPluginDispatcher.execute(
                pluginDispatcher, upstreamContext, url, taskCode,
                () -> exchange(url, access, method));
    }

    /**
     * 发一次查询并取回响应体原文；非 2xx 一律转成携带状态码与错误体的上游异常，
     * 让插件侧能按状态码筛选、调用侧照旧按 {@code RuntimeException} 退避重试。
     * 包级可见供单测直接驱动。
     */
    String exchange(String url, UpstreamAccess access, HttpMethod method) {
        // ⭐ 用异步任务自己的查询超时，不是 knot.gateway.upstream.response-timeout：
        // 后者是给生成型请求的（等模型吐字，默认 120s），拿它来查一次任务状态，
        // 超时会比 claim_ttl（默认 180s）之外的预算还难对齐——旧代码就是这样把
        // 「占位 60s / 查询 120s」凑到一起，查询还没回来占位就已经能被别的节点回收。
        // 下限 1s 兜住误配成 0 或负数（block(0) 是「立即超时」，不是「不限时」）
        Duration timeout = Duration.ofMillis(Math.max(1_000L, properties.getQueryTimeoutMs()));
        return webClient.method(method)
                // ⚠ 用 URI.create 而不是 uri(String)：后者会做一层 URI 模板编码，
                // 与「禁止对上游 URL 做编解码」的红线冲突（Bedrock 路径含已编码的 %3A）
                .uri(URI.create(url))
                .headers(headers -> headers.addAll(access.headers()))
                .retrieve()
                // defaultIfEmpty 兜住「错误响应没有 body」：onStatus 的映射器返回空 Mono 时
                // 错误信号会被吞掉，调用方会把一次 4xx 当成「上游没给内容」继续往下走
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new GatewayUpstreamException(
                                "upstream responded with status " + response.statusCode().value(),
                                ProxyErrorCodeEnum.UPSTREAM_ERROR.code(),
                                response.statusCode().value(),
                                body)))
                .bodyToMono(String.class)
                .block(timeout);
    }
}
