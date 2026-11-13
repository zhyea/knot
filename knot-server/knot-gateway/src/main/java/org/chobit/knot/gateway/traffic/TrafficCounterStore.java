package org.chobit.knot.gateway.traffic;

/**
 * 频控 / 额度的计数存储抽象。
 * 抽象的下限是「一次调用完成一次原子累加」，不是「读出来再加回去」；业务语义（限多少、算不算超）一律留在上层，存储层只认「键 + 增量 + 过期时刻」。
 * 两种实现：单节点走 {@link CaffeineTrafficCounterStore}，多节点走 {@link RedisTrafficCounterStore}，由 {@code knot.traffic.store} 选择。
 */
public interface TrafficCounterStore {

    /**
     * 存储实现标识，用于启动日志与排查（{@code caffeine} / {@code redis}）。
     */
    String id();

    /**
     * 原子累加并返回累加后的值。
     *
     * @param key            存储键
     * @param delta          增量，必须为正数
     * @param expireAtMillis 条目过期时刻（epoch millis）
     * @return 累加后的计数值
     */
    long addAndGet(String key, long delta, long expireAtMillis);

    /**
     * 读取当前计数值，不存在则返回 {@code 0}；不创建条目。
     */
    long get(String key);
}
