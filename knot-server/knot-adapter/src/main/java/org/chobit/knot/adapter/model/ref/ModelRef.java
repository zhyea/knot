package org.chobit.knot.adapter.model.ref;

/**
 * 适配层自有的模型投影：只带上游调用链路真正消费的字段。
 *
 * @param id               模型主键（上游响应归因 / 插件上下文用；跨模块绑定仍走 code）
 * @param modelCode        模型业务码
 * @param upstreamModel    写入上游请求体 model 参数的标识
 * @param basePath         模型级 URL 前缀（与账户 base_url 拼接）
 * @param billingRuleCode  显式绑定的计费规则业务码，可为空（空则按模型族回退）
 * @param modelFamilyCode  模型族业务码，计费规则族级回退的匹配键
 */
public record ModelRef(Long id,
                       String modelCode,
                       String upstreamModel,
                       String basePath,
                       String billingRuleCode,
                       String modelFamilyCode) {
}
