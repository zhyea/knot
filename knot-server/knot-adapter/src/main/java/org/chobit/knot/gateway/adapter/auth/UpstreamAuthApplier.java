package org.chobit.knot.gateway.adapter.auth;

import org.chobit.knot.gateway.adapter.upstream.UpstreamAccess;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.adapter.upstream.UpstreamSignableRequest;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.http.HttpHeaders;

import java.util.Set;

/**
 * 上游接入策略。
 *
 * <p>一个策略 encapsulates 了「某个供应商该怎么把凭据落到上游请求上」的全部知识：
 * 从 {@code authConfig} 取哪些凭据字段、投到哪个头、该厂商要求的协议固定头
 * （如 anthropic-version），以及需要派生时上游 base URL 从哪来。请求适配器不参与
 * 鉴权与端点解析，只负责请求/响应转换。</p>
 *
 * <p><b>供应商特例只能存在于供应商自己的策略里</b>：通用接口的 default 方法禁止出现
 * 供应商名称、供应商专用工具或只对某供应商有意义的凭据字段判断。</p>
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
     * 上游接入准备：一次性产出生效的 base URL 与静态鉴权头。
     *
     * <p>执行器每个请求只调用一次，结果贯穿请求构造、路径拼接与签名。默认实现把
     * {@link #baseUrl} 与 {@link #apply} 组合起来；策略无需重写本方法，需要切换
     * URL 来源时覆写 {@link #baseUrl} 即可。</p>
     */
    default UpstreamAccess prepare(UpstreamRequestContext context) {
        HttpHeaders headers = new HttpHeaders();
        apply(headers, context);
        return new UpstreamAccess(baseUrl(context), headers);
    }

    /**
     * 生效的上游 base URL。
     *
     * <p>默认只认供应商账户配置的上游地址（{@code kb_provider_accounts.base_url}），
     * <b>不含任何供应商特例</b>。需要按凭据派生端点的策略（AWS Bedrock）覆写本方法。</p>
     *
     * <p>返回 {@code null} 表示配置缺失，由执行器统一报
     * {@code UPSTREAM_ENDPOINT_NOT_CONFIGURED}——本层不抛业务异常，
     * 以免 adapter 反向依赖 gateway 的异常类型。</p>
     */
    default String baseUrl(UpstreamRequestContext context) {
        return context == null || context.provider() == null ? null : context.provider().getBaseUrl();
    }

    /**
     * 该策略是否要求账户显式配置 base URL。
     *
     * <p>为 {@code false} 表示端点可由凭据派生（如 AWS 按 region + endpointType），
     * 账户保存时不强制填写、运行时允许为空。默认 {@code true}。</p>
     */
    default boolean requiresBaseUrl() {
        return true;
    }

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
     * 该策略的签名是否覆盖 payload 哈希——为 {@code true} 时执行器必须先把请求体序列化成
     * 最终字节，算好哈希再回调 {@link #sign}，并以同一份字节发出。
     *
     * <p>命名刻意避开「给 body 单独签名」的误解：SigV4 并没有独立的 body 签名头，
     * 是 payload 的 SHA-256 进入 canonical request。语义等价于
     * 「requiresFinalizedPayload（必须等请求体定稿才能签名）」。</p>
     */
    default boolean requiresFinalizedPayload() {
        return false;
    }
}
