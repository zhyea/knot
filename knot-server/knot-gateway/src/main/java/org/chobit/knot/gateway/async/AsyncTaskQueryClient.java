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
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ProviderAccountEntity;
import org.chobit.knot.dal.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
import org.chobit.knot.gateway.upstream.support.UpstreamUrlSupport;
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
 *
 * <p><b>只做一件事</b>：把「查上游」做成一次普通的 GET（或档案声明的其它方法），
 * 响应体原文交给上游的提取/计费管线。这里不解析任何业务字段 ——
 * 一旦在客户端里顺手取一次 status，判定口径就有了两份（档案配置 + 客户端代码），必然漂移。</p>
 *
 * <p>鉴权与 URL 一律复用既有原语（{@code UpstreamAuthApplier} + {@code UpstreamUrlSupport}），
 * 不另写一套：地址拼错或鉴权头少一个，在异步场景下的表现是「任务永远查不到」，极难定位。</p>
 *
 * <p>⭐ <b>超时是并发控制的一部分</b>：查询必须在 {@code claim_ttl} 之内结束，否则占位先过期、
 * 别的节点把同一行重新领走，同一次上游调用会被两个节点各记一遍。故超时取
 * {@link AsyncTaskProperties#getQueryTimeoutMs()}，启动期由 {@code AsyncTaskPoller#init}
 * 校验「查询超时 &lt; 占位有效期」。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskQueryClient implements AsyncTaskQueryPort {

    private final WebClient webClient;
    private final AsyncTaskProperties properties;
    private final GatewayEntityCacheService dataService;
    private final ProviderCredentialSupport credentialSupport;
    private final UpstreamAuthApplierCatalog authApplierCatalog;

    /**
     * 查询一次任务状态。
     *
     * @return 响应体原文；查询失败抛异常，由调用方按退避处理（不在这里吞）
     */

    @Override
    public String query(AsyncTaskContext context,
                        AsyncTaskSpec spec,
                        String upstreamTaskId) {
        ModelEntity model = dataService.getModelByCode(context.modelCode());
        if (model == null) {
            throw new IllegalStateException("async task model not found: " + context.modelCode());
        }
        ProviderAccountEntity provider = dataService.getProviderAccountByCode(model.getProviderAccountCode());
        if (provider == null) {
            throw new IllegalStateException("async task provider not found for model: " + context.modelCode());
        }
        ProviderCredentialEntity credential = dataService.getActiveCredentialByProviderId(provider.getId());
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode(context.protocol());
        UpstreamRequestContext upstreamContext = new UpstreamRequestContext(
                protocol,
                Map.of(),
                MediaType.APPLICATION_JSON,
                new ModelRef(model.getId(), model.getModelCode(), model.getUpstreamModel(),
                        model.getBasePath(), model.getBillingRuleCode(), model.getModelFamilyCode()),
                new ProviderAccountRef(provider.getId(), provider.getCode(), provider.getBaseUrl(),
                        provider.getProviderCode()),
                credentialSupport.toAuthConfig(credential),
                null,
                context.traceparent(),
                GatewayTraceContext.traceIdOf(context.traceparent()),
                credentialSupport.authApplier(credential),
                context.requestId());
        UpstreamAuthApplier applier = authApplierCatalog.resolve(upstreamContext.authApplier());
        UpstreamAccess access = applier == null ? null : applier.prepare(upstreamContext);
        if (access == null || access.baseUrl() == null) {
            throw new IllegalStateException("async task upstream endpoint not configured: " + context.modelCode());
        }
        String path = spec == null ? null : spec.resolvePath(upstreamTaskId);
        String url = UpstreamUrlSupport.joinUrl(access.baseUrl(),
                UpstreamUrlSupport.joinPath(model.getBasePath(), path));
        return exchange(url, access, spec == null ? HttpMethod.GET : HttpMethod.valueOf(spec.queryMethod().toUpperCase()));
    }

    private String exchange(String url, UpstreamAccess access, HttpMethod method) {
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
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> new IllegalStateException(
                                "async task query failed: status=" + response.statusCode() + " body=" + body)))
                .bodyToMono(String.class)
                .block(timeout);
    }
}
