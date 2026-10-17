package org.chobit.knot.gateway.dto.routing;

/**
 * 计费规则的命中层级（三级回退）：决定「哪一级规则」实际参与了当次计费。
 *
 * <p>顺序即回退优先级：模型显式绑定 → 模型族默认 → 全局默认 → 全部缺失。
 * 自检层 {@code RULE_SOURCE_EXPECTED} 以 {@link #EXPLICIT} 为期望层级，
 * 模型静默回退到族/全局默认时即标 FAIL（见设计稿 v7 §5）。</p>
 */
public enum BillingRuleSource {

    /** 模型显式绑定的规则码命中（期望层级） */
    EXPLICIT,

    /** 模型族默认规则命中（模型未显式绑定时） */
    FAMILY,

    /** 全局默认规则命中（模型族也无专属规则时） */
    GLOBAL,

    /** 三级均未命中，当次无计费规则 */
    MISSING
}
