package org.chobit.knot.gateway.adapter.model.ref;

/**
 * 适配层自有的模型投影：只带上游调用链路真正消费的字段。
 *
 * <p>2026-10-24 依赖倒挂修复（改进清单 2.5）：适配层不再 import {@code knot-dal} 的
 * {@code ModelEntity}，由同时依赖 dal 与 adapter 的网关侧在构造
 * {@link UpstreamRequestContext} 时投影。实体后续加列（展示字段、审计字段）
 * 不会漏进适配层契约，适配层也不必为看不见的字段承担编译耦合。</p>
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
