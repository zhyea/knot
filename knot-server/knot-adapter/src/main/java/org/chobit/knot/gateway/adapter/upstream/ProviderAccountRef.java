package org.chobit.knot.gateway.adapter.upstream;

/**
 * 适配层自有的供应商账户投影：只带上游调用链路真正消费的字段。
 *
 * <p>2026-10-24 依赖倒挂修复（改进清单 2.5）：适配层不再 import {@code knot-dal} 的
 * {@code ProviderAccountEntity}，由网关侧在构造 {@link UpstreamRequestContext} 时投影。</p>
 *
 * @param id            账户主键（上游响应归因 / 插件上下文用）
 * @param code          账户业务码
 * @param baseUrl       账户接入地址（允许带路径前缀，与模型 base_path 段级拼接）
 * @param providerCode  所属供应商业务码（提取器按供应商兜底时用，如 Anthropic 缓存口径）
 */
public record ProviderAccountRef(Long id,
                                 String code,
                                 String baseUrl,
                                 String providerCode) {
}
