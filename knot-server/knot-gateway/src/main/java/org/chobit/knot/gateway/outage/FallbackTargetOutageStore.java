package org.chobit.knot.gateway.outage;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.model.EjectionPolicy;

import java.util.List;

/**
 * 摘除存储的降级适配器：<b>Redis 优先，不可用时自动降级 Caffeine</b>。
 *
 * <p>解决一个具体缺口：多节点部署必须用 Redis 才能拿到全局摘除视图，但 Redis 一旦抖动，
 * 原来的选择只有两种坏结果——按「健康」放行（{@code fail-open}，摘除能力整体消失，
 * 坏目标重新开始吃流量）或直接抛错（缓存故障演变成网关选路故障）。本适配器给出第三条路：
 * <b>降级到单节点近似</b>，保住摘除能力，等 Redis 恢复。</p>
 *
 * <p>三种部署形态各得其所：</p>
 * <ul>
 *   <li>多节点 + Redis 健康：全局共享视图；</li>
 *   <li>多节点 + Redis 抖动：降级为各节点本地视图（近似），功能不中断；</li>
 *   <li>单节点没装 Redis：以 {@code fallback} 档启动即等价于直接用 caffeine。</li>
 * </ul>
 *
 * <p><b>固有限制（必须知晓）</b>：降级期间的写入只进本地 Caffeine，Redis 恢复时这些状态
 * 不自动回灌——本实现只回灌「恢复时刻本地仍处于摘除中」的条目（见 {@link #rehydrate}），
 * 恢复前的窗口计数/探活租约会丢失。若要求严格一致，应配置 {@code redis} 纯档 + 外部保证
 * Redis 高可用。</p>
 *
 * <p>恢复探测是<b>节流</b>的（默认 10s 一次）：降级后每次读写都去探 Redis 会把故障放大成
 * 「Redis 慢 → 探活更慢 → 请求更慢」的正反馈。</p>
 */
@Slf4j
public class FallbackTargetOutageStore implements TargetOutageStore {

    private final TargetOutageStore primary;
    private final TargetOutageStore fallback;
    private final OutageKeys keys;
    private final long probeIntervalMs;
    private final RedisHealthProbe healthProbe;

    private volatile boolean degraded;
    private volatile long lastProbeAt;

    /**
     * Constructs a new instance.
     *
     * @param primary         主存储（Redis）
     * @param fallback        降级存储（Caffeine）
     * @param keys            键工厂（回灌时按 ruleCode/modelCode 重建键）
     * @param probeIntervalMs 恢复探测间隔（毫秒）
     * @param healthProbe     主存储健康探针（ping 之类；异常即视为不可用）
     */
    public FallbackTargetOutageStore(TargetOutageStore primary,
                                     TargetOutageStore fallback,
                                     OutageKeys keys,
                                     long probeIntervalMs,
                                     RedisHealthProbe healthProbe) {
        this.primary = primary;
        this.fallback = fallback;
        this.keys = keys;
        this.probeIntervalMs = Math.max(1_000L, probeIntervalMs);
        this.healthProbe = healthProbe;
    }

    /**
     * 主存储健康探针；注入成函数以便单测用假的「可用/不可用」开关驱动。
     */
    @FunctionalInterface
    public interface RedisHealthProbe {
        void probe();
    }

    /**
     * 当前是否处于降级状态。
     */
    public boolean degraded() {
        return degraded;
    }

    @Override
    public String id() {
        return degraded ? fallback.id() + " (fallback)" : primary.id();
    }

    @Override
    public OutageState state(String key, long nowMillis) {
        if (degraded) {
            recoverIfDue(nowMillis);
            if (degraded) {
                return fallback.state(key, nowMillis);
            }
        }
        try {
            return primary.state(key, nowMillis);
        } catch (RuntimeException ex) {
            degrade("state", key, ex);
            return fallback.state(key, nowMillis);
        }
    }

    @Override
    public OutageState recordAnomaly(String key, EjectionPolicy policy, AnomalyKind kind, long nowMillis) {
        if (degraded) {
            recoverIfDue(nowMillis);
            if (degraded) {
                return fallback.recordAnomaly(key, policy, kind, nowMillis);
            }
        }
        try {
            return primary.recordAnomaly(key, policy, kind, nowMillis);
        } catch (RuntimeException ex) {
            degrade("recordAnomaly", key, ex);
            return fallback.recordAnomaly(key, policy, kind, nowMillis);
        }
    }

    @Override
    public void recordSuccess(String key, boolean probing, long nowMillis) {
        if (degraded) {
            recoverIfDue(nowMillis);
            if (degraded) {
                fallback.recordSuccess(key, probing, nowMillis);
                return;
            }
        }
        try {
            primary.recordSuccess(key, probing, nowMillis);
        } catch (RuntimeException ex) {
            degrade("recordSuccess", key, ex);
            fallback.recordSuccess(key, probing, nowMillis);
        }
    }

    @Override
    public boolean tryAcquireProbe(String key, long probeTimeoutMs, long nowMillis) {
        if (degraded) {
            recoverIfDue(nowMillis);
            if (degraded) {
                return fallback.tryAcquireProbe(key, probeTimeoutMs, nowMillis);
            }
        }
        try {
            return primary.tryAcquireProbe(key, probeTimeoutMs, nowMillis);
        } catch (RuntimeException ex) {
            degrade("tryAcquireProbe", key, ex);
            return fallback.tryAcquireProbe(key, probeTimeoutMs, nowMillis);
        }
    }

    @Override
    public void release(String key) {
        runOnActive(() -> {
            releaseBoth(key);
            return null;
        });
    }

    @Override
    public void forceEject(String key, long cooldownMs, long nowMillis) {
        runOnActive(() -> {
            forceBoth(key, cooldownMs, nowMillis);
            return null;
        });
    }

    @Override
    public List<OutageEntry> list(long nowMillis) {
        if (degraded) {
            recoverIfDue(nowMillis);
            if (degraded) {
                return fallback.list(nowMillis);
            }
        }
        try {
            return primary.list(nowMillis);
        } catch (RuntimeException ex) {
            degrade("list", "pattern:" + keys.pattern(), ex);
            return fallback.list(nowMillis);
        }
    }

    /**
     * 运维动作（release / forceEject）走「当前生效存储 + 另一侧尽力同步」：
     * 降级期间点了恢复，Redis 里可能还留着旧状态，必须一并清掉，否则恢复后旧摘除复活。
     */
    private void runOnActive(java.util.function.Supplier<Void> action) {
        if (degraded) {
            recoverIfDue(System.currentTimeMillis());
        }
        action.get();
    }

    private void releaseBoth(String key) {
        try {
            primary.release(key);
        } catch (RuntimeException ex) {
            log.warn("Outage store degraded on release sync, key={}: {}", key, ex.getMessage());
            degraded = true;
        }
        fallback.release(key);
    }

    private void forceBoth(String key, long cooldownMs, long nowMillis) {
        try {
            primary.forceEject(key, cooldownMs, nowMillis);
        } catch (RuntimeException ex) {
            log.warn("Outage store degraded on forceEject sync, key={}: {}", key, ex.getMessage());
            degraded = true;
        }
        fallback.forceEject(key, cooldownMs, nowMillis);
    }

    /**
     * 标记降级。
     *
     * <p>同时把恢复探测的时刻对齐到「本次故障发生」——这次失败本身就是最可靠的探活结果，
     * 从降级时刻起算节流窗口，避免 Redis 故障期间每次读写都去重复撞墙。</p>
     */
    private void degrade(String operation, String key, RuntimeException ex) {
        if (!degraded) {
            degraded = true;
            lastProbeAt = System.currentTimeMillis();
            log.warn("Primary outage store unavailable on {}, degraded to {}: key={}, {}",
                    operation, fallback.id(), key, ex.getMessage());
        }
    }

    /**
     * 按间隔尝试恢复：探针通过则回灌本地仍摘除中的条目，切回主存储。
     *
     * <p>回灌口径：只搬「恢复时刻本地仍处于摘除/探活中」的条目，剩余冷却时长
     * {@code ejectedUntil - now} 作为主存储的冷却；已恢复的条目不搬（主存储自己的状态优先）。
     * 窗口计数与探活租约不搬——降级期间的本地过程量，丢失不影响正确性。</p>
     *
     * <p>恢复成功后，<b>当次读写直接走主存储</b>（调用方见
     * {@link #state}/{@link #recordAnomaly} 等：recover 后不再穿透到 fallback），
     * 否则刚回灌的摘除状态会在恢复瞬间的那一跳被漏掉。</p>
     */
    private void recoverIfDue(long nowMillis) {
        if (nowMillis - lastProbeAt < probeIntervalMs) {
            return;
        }
        lastProbeAt = nowMillis;
        try {
            healthProbe.probe();
        } catch (RuntimeException ex) {
            return;
        }
        for (OutageEntry entry : fallback.list(nowMillis)) {
            long remaining = entry.state().ejectedUntil() - nowMillis;
            if (remaining <= 0L) {
                continue;
            }
            try {
                primary.forceEject(keys.of(entry.ruleCode(), entry.modelCode()), remaining, nowMillis);
            } catch (RuntimeException ex) {
                log.warn("Outage store rehydrate failed, key={}: {}",
                        keys.of(entry.ruleCode(), entry.modelCode()), ex.getMessage());
                return;
            }
            fallback.release(keys.of(entry.ruleCode(), entry.modelCode()));
        }
        degraded = false;
        log.info("Primary outage store recovered, switched back to {}", primary.id());
    }
}
