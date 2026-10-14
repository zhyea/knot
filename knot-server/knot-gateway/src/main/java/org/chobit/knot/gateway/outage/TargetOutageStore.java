package org.chobit.knot.gateway.outage;

import org.chobit.knot.gateway.model.EjectionPolicy;

import java.util.List;

/**
 * 「规则 × 供应商模型」异常摘除状态的运行时存储。
 *
 * <p>与 {@link org.chobit.knot.gateway.load.ModelLoadStore} 同构：接口 + caffeine/redis 双实现 +
 * fail-open，由 {@code knot.outage.store} 二选一。不复用在途计量的原因是语义不同——
 * 那是可增可减的 gauge，这是带窗口与冷却的状态条目。</p>
 *
 * <p><b>故障一律 fail-open</b>：读不到状态时按「健康」处理（不摘除）。
 * 缓存故障绝不能演变成网关选路故障。</p>
 */
public interface TargetOutageStore {

    /**
     * 存储实现标识，用于启动日志与排查（{@code caffeine} / {@code redis}）。
     */
    String id();

    /**
     * 读取当前状态；条目不存在或存储不可用时返回 {@link OutageState#HEALTHY}（fail-open）。
     */
    OutageState state(String key, long nowMillis);

    /**
     * 记录一次异常并返回新状态（是否因此被摘除由返回的 {@code ejectedUntil} 体现）。
     */
    OutageState recordAnomaly(String key, EjectionPolicy policy, AnomalyKind kind, long nowMillis);

    /**
     * 记录一次成功：探活成功即清除状态；普通成功只清零窗口计数。
     */
    void recordSuccess(String key, boolean probing, long nowMillis);

    /**
     * 抢占探活租约：冷却已过且租约空闲时返回 {@code true}（调用方据此放行本次请求作为探活）。
     */
    boolean tryAcquireProbe(String key, long probeTimeoutMs, long nowMillis);

    /**
     * 手动恢复：清除整条状态。
     */
    void release(String key);

    /**
     * 手动摘除：直接进入冷却，不依赖窗口计数。
     */
    void forceEject(String key, long cooldownMs, long nowMillis);

    /**
     * 列举当前非健康条目，管理面使用（低频）。
     */
    List<OutageEntry> list(long nowMillis);
}
