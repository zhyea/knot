package org.chobit.knot.gateway.outage;

import org.chobit.knot.common.model.EjectionPolicy;

import java.util.List;

/**
 * 「规则 × 供应商模型」异常摘除状态的运行时存储。
 *
 * <p>不复用在途计量接口（{@link org.chobit.knot.gateway.load.ModelLoadStore}）的原因是语义不同：
 * 那是可增可减的 gauge，这是带窗口与冷却的状态条目。</p>
 *
 * <p><b>只有进程内 Caffeine 一个实现</b>：摘除表达的是「本节点自己观察到的异常」，
 * 本就不需要跨节点共享（原 Redis 实现与降级档已于 2026-10-15 下线）。
 * 因此没有 {@code store} 开关，也不存在存储故障——
 * 读不到条目就是「健康」（不摘除），不存在 fail-open 开关需要配。</p>
 */
public interface TargetOutageStore {

    /**
     * 存储实现标识，用于启动日志与排查（固定 {@code caffeine}）。
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
