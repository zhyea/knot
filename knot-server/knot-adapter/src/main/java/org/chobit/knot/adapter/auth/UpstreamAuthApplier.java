package org.chobit.knot.adapter.auth;

import org.chobit.knot.adapter.upstream.UpstreamAccess;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.adapter.upstream.UpstreamSignableRequest;
import org.chobit.knot.common.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.http.HttpHeaders;

import java.util.Set;

/**
 * 上游接入策略：定义某供应商如何把凭据落到上游请求上（取哪些凭据字段、投到哪个头、
 * 厂商协议固定头如 {@code anthropic-version}），以及需要派生时上游 base URL 从哪来。
 * 请求适配器只做请求/响应转换，不参与鉴权与端点解析；供应商特例只能写在该供应商自己的策略里。
 */
public interface UpstreamAuthApplier {

    String code();

    String label();

    /**
     * 该策略适用的供应商认证类型（{@link ProviderCredentialTypeEnum}）。
     * 前端据此按已选认证类型筛选可选鉴权方式；后端保存时也会据此校验。
     */
    Set<ProviderCredentialTypeEnum> credentialTypes();

    /**
     * 上游接入准备：一次性产出生效的 base URL 与静态鉴权头，执行器每个请求调用一次。
     * 默认实现组合 {@link #baseUrl} 与 {@link #apply}；切换 URL 来源时覆写 {@link #baseUrl}。
     */
    default UpstreamAccess prepare(UpstreamRequestContext context) {
        HttpHeaders headers = new HttpHeaders();
        apply(headers, context);
        return new UpstreamAccess(baseUrl(context), headers);
    }

    /**
     * 生效的上游 base URL：默认取 {@code kb_provider_accounts.base_url}，需按凭据派生端点的策略覆写本方法。
     * 返回 {@code null} 表示配置缺失，由执行器报 {@code UPSTREAM_ENDPOINT_NOT_CONFIGURED}；本层不抛业务异常。
     */
    default String baseUrl(UpstreamRequestContext context) {
        return context == null || context.provider() == null ? null : context.provider().baseUrl();
    }

    /**
     * 该策略是否要求账户显式配置 base URL：{@code false} 表示端点可由凭据派生，默认 {@code true}。
     */
    default boolean requiresBaseUrl() {
        return true;
    }

    /**
     * 把该供应商的鉴权头（含凭据与协议固定头）写入请求；缺少凭据时静默跳过。
     * 只操作 {@link HttpHeaders}，不绑定任何 HTTP 客户端实现类型。
     */
    void apply(HttpHeaders headers, UpstreamRequestContext context);

    /**
     * 请求签名钩子：需要对请求本身签名的策略（AWS SigV4）覆写本方法，把签名头写进 {@code headers}；默认空实现。
     * 执行器在请求体定稿后回调，签名所用字节须与实际发出的字节为同一份。
     */
    default void sign(UpstreamSignableRequest request,
                      HttpHeaders headers,
                      UpstreamRequestContext context) {
    }

    /**
     * 该策略的签名是否覆盖 payload 哈希：{@code true} 时执行器必须先把请求体序列化为最终字节，
     * 算好哈希再回调 {@link #sign}，并以同一份字节发出。
     */
    default boolean requiresFinalizedPayload() {
        return false;
    }
}
