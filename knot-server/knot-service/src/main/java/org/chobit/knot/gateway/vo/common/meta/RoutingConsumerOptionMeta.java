package org.chobit.knot.gateway.vo.common.meta;

/**
 * 路由消费者下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('consumerCode', c.consumer_code)} 对齐）。
 *
 * <p>消费者 options 的 {@code value} 是 {@code kb_routing_consumers.consumer_code}（业务码，非主键 id），
 * 消费码 {@code consumerCode} 同时作为 value 与 meta 冗余展示，便于绑定走 code 而非 id（见路由规则↔消费者 1:1 收敛）。</p>
 *
 * <p>敏感字段纪律：本类<b>不得</b>扩出 secretKey / 凭据 / 完整 configJson（见约束第 0 节）。
 * SQL 侧同样不 select 这些列。</p>
 */
public record RoutingConsumerOptionMeta(String consumerCode) {
}
