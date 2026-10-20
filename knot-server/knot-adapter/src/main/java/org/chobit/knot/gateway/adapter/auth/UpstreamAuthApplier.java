package org.chobit.knot.gateway.adapter.auth;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.http.HttpHeaders;

import java.util.Set;

/**
 * 上游鉴权策略。
 *
 * <p>一个策略 encapsulates 了「某个供应商该怎么把凭据落到上游请求上」的全部知识：
 * 从 {@code authConfig} 取哪些凭据字段、投到哪个头、以及该厂商要求的协议固定头
 * （如 anthropic-version）。请求适配器不参与鉴权，只负责请求/响应转换。</p>
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
     * 把该供应商的鉴权头（含凭据与协议固定头）写入请求。缺少凭据时静默跳过。
     *
     * <p>只碰 {@link HttpHeaders} 而不碰任何 HTTP 客户端类型：鉴权策略属于协议知识，
     * 不该被绑定在 RestClient / WebClient 某一种客户端实现上（2026-10-20 上游客户端
     * 迁 WebClient 时据此解耦，knot-adapter 无需依赖 webflux）。</p>
     */
    void apply(HttpHeaders headers, UpstreamRequestContext context);
}
