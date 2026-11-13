package org.chobit.knot.adapter.model.ref;

/**
 * 适配层自有的供应商账户投影：只带上游调用链路真正消费的字段。
 *
 * @param id            账户主键（上游响应归因 / 插件上下文用）
 * @param code          账户业务码
 * @param baseUrl       账户接入地址（允许带路径前缀，与模型 base_path 段级拼接）
 * @param providerCode  所属供应商业务码（上游调用日志 / 归因用；提取器选择不按供应商猜，
 *                      一律走 {@code kb_model_api_bindings.usage_extractor} 绑定配置）
 */
public record ProviderAccountRef(Long id,
                                 String code,
                                 String baseUrl,
                                 String providerCode) {
}
