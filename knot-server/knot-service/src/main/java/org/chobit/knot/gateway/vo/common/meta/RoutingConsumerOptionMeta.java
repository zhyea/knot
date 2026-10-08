package org.chobit.knot.gateway.vo.common.meta;

/**
 * 路由消费者下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('consumerCode', c.consumer_code)} 对齐）。
 *
 * <p>消费者 options 的 {@code value} 是 {@code kb_routing_consumers.id}，消费码 {@code consumerCode} 是
 * 表单表格列要展示的业务属性，因此落在 meta 而非顶层（顶层 {@code code} 已按 options-refactor-constraints.md 移除）。</p>
 *
 * <p>敏感字段纪律：本类<b>不得</b>扩出 secretKey / 凭据 / 完整 configJson（见约束第 0 节）。
 * SQL 侧同样不 select 这些列。</p>
 */
public record RoutingConsumerOptionMeta(String consumerCode) {
}
