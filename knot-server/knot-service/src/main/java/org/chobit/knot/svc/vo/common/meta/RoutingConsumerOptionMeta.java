package org.chobit.knot.svc.vo.common.meta;

/**
 * 路由消费者下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('consumerCode', c.consumer_code)} 对齐）。
 * {@code value} 是 {@code kb_routing_consumers.consumer_code}（业务码，非主键 id）；{@code consumerCode}
 * 同时作为 value 与 meta 冗余展示，绑定走 code 而非 id。
 * 本类不得扩出 {@code secretKey} / 凭据 / 完整 {@code configJson}，SQL 侧同样不 select 这些列。
 */
public record RoutingConsumerOptionMeta(String consumerCode) {
}
