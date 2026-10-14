package org.chobit.knot.gateway.outage;

/**
 * 单个候选在本次请求中的摘除判定结果。
 *
 * @param key    该「规则 × 模型」的存储键
 * @param state  判定前的状态快照（用于判断是否「本次才摘除」，避免重复打日志）
 * @param skip   true 表示该候选当前被摘除，本次请求应跳过
 * @param probing true 表示本次是冷却结束后放行的探活请求
 * @param tracked 策略已启用且需回写结果；false 时调用方不必再记成功/失败
 */
public record EjectionDecision(String key,
                               OutageState state,
                               boolean skip,
                               boolean probing,
                               boolean tracked) {
}
