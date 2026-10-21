package org.chobit.knot.gateway.adapter.auth;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.adapter.upstream.UpstreamSignableRequest;
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

    /**
     * 请求签名钩子：需要「对请求本身签名」的策略（AWS SigV4）覆写本方法，
     * 把签名头写进 {@code headers}。默认空实现——静态头策略无需签名。
     *
     * <p>与 {@link #apply} 的分工：{@code apply} 只按凭据拼静态头，拿不到 URL 与请求体；
     * SigV4 的签名覆盖「方法 + 路径 + host + 请求体哈希 + 时间戳」，必须由执行器在
     * 请求体定稿后回调，且签名用的字节与实际发出的字节是同一份（第一雷区）。</p>
     */
    default void sign(UpstreamSignableRequest request,
                      HttpHeaders headers,
                      UpstreamRequestContext context) {
    }

    /**
     * 该策略是否需要对请求体做签名。为 {@code true} 时执行器先把请求体序列化成字节、
     * 算好哈希再回调 {@link #sign}，并以同一份字节发出。
     */
    default boolean signsRequestBody() {
        return false;
    }
}
