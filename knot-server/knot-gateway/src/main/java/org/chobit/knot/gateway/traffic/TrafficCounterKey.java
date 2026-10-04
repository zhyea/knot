package org.chobit.knot.gateway.traffic;

/**
 * 频控 / 额度计数键。
 *
 * <p>{@code key} 是最终写入存储的字符串（Caffeine 的 key、Redis 的 key 完全一致，
 * 便于两种实现之间无缝切换）；{@code expireAtMillis} 是该计数窗口的结束时刻（绝对毫秒），
 * 由调用方随写入一起交给存储，存储层不需要知道任何业务窗口语义。</p>
 *
 * @param key            存储键
 * @param expireAtMillis 窗口结束时刻（epoch millis），用于设置 TTL
 */
public record TrafficCounterKey(String key, long expireAtMillis) {
}
