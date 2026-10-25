package org.chobit.knot.gateway.outage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import org.chobit.knot.common.model.EjectionPolicy;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 单节点摘除状态存储：Caffeine。
 *
 * <p>三条实现要点（与 {@code CaffeineModelLoadStore} 同构）：</p>
 * <ol>
 *   <li><b>原子性</b>：走 {@code cache.asMap().compute}，Caffeine 对同一 key 的计算是排他的，
 *       并发请求不会丢计数、也不会重复抢占探活租约。</li>
 *   <li><b>逐键过期</b>：用 {@link Expiry} 按条目自身携带的到期时刻计算 TTL，
 *       窗口 / 冷却 / 租约全部过期后条目自动消失，无需清理任务。</li>
 *   <li><b>恢复即删</b>：状态回到健康时直接移除条目，不保留零值条目占堆。</li>
 * </ol>
 *
 * <p>局限：只反映本进程观测到的异常，多节点部署下各节点各自摘除（近似口径）；
 * 需要全局一致切到 {@code RedisTargetOutageStore}。</p>
 */
public class CaffeineTargetOutageStore implements TargetOutageStore {

    private final Cache<String, OutageState> cache;
    private final long graceMillis;

    /**
     * Constructs a new instance.
     *
     * @param maximumSize 最大条目数
     * @param graceMillis 到期后的保留宽限（毫秒）
     */
    public CaffeineTargetOutageStore(long maximumSize, long graceMillis) {
        this(maximumSize, graceMillis, Clock.systemUTC(), Ticker.systemTicker());
    }

    /**
     * Constructs a new instance with injectable time sources.
     *
     * @param maximumSize 最大条目数
     * @param graceMillis 到期后的保留宽限（毫秒）
     * @param clock       墙钟，测试可注入固定时钟
     * @param ticker      Caffeine 的时间源，测试可注入
     */
    public CaffeineTargetOutageStore(long maximumSize, long graceMillis, Clock clock, Ticker ticker) {
        this.graceMillis = graceMillis;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(ticker)
                .executor(Runnable::run)
                .expireAfter(new StateExpiry(clock, graceMillis))
                .build();
    }

    @Override
    public String id() {
        return "caffeine";
    }

    @Override
    public OutageState state(String key, long nowMillis) {
        OutageState state = cache.getIfPresent(key);
        return state == null ? OutageState.HEALTHY : state;
    }

    @Override
    public OutageState recordAnomaly(String key, EjectionPolicy policy, AnomalyKind kind, long nowMillis) {
        return cache.asMap()
                .compute(key, (ignored, existing) -> OutageStateMachine.recordAnomaly(existing, policy, kind, nowMillis));
    }

    @Override
    public void recordSuccess(String key, boolean probing, long nowMillis) {
        cache.asMap().compute(key, (ignored, existing) -> {
            OutageState next = OutageStateMachine.recordSuccess(existing, probing, nowMillis);
            return next.anomalies() == 0 && !next.ejected(nowMillis) && !next.probing(nowMillis) ? null : next;
        });
    }

    @Override
    public boolean tryAcquireProbe(String key, long probeTimeoutMs, long nowMillis) {
        boolean[] acquired = {false};
        cache.asMap().compute(key, (ignored, existing) -> {
            OutageState state = existing == null ? OutageState.HEALTHY : existing;
            if (state.ejected(nowMillis) || state.probing(nowMillis)) {
                return state;
            }
            acquired[0] = true;
            return OutageStateMachine.acquireProbe(state, probeTimeoutMs, nowMillis);
        });
        return acquired[0];
    }

    @Override
    public void release(String key) {
        cache.invalidate(key);
    }

    @Override
    public void forceEject(String key, long cooldownMs, long nowMillis) {
        cache.put(key, OutageStateMachine.forceEject(cooldownMs, nowMillis));
    }

    @Override
    public List<OutageEntry> list(long nowMillis) {
        List<OutageEntry> result = new ArrayList<>();
        for (Map.Entry<String, OutageState> entry : cache.asMap().entrySet()) {
            OutageState state = entry.getValue();
            if (state == null || !(state.ejected(nowMillis) || state.probing(nowMillis))) {
                continue;
            }
            OutageEntry parsed = parseEntry(entry.getKey(), state);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    /**
     * 从 {@code <prefix>:RULE:<ruleCode>:MODEL:<modelCode>} 反解出规则与模型码。
     */
    private static OutageEntry parseEntry(String key, OutageState state) {
        int ruleIndex = key.indexOf(":RULE:");
        int modelIndex = key.indexOf(":MODEL:", ruleIndex + 1);
        if (ruleIndex < 0 || modelIndex < 0) {
            return null;
        }
        return new OutageEntry(key.substring(ruleIndex + 6, modelIndex), key.substring(modelIndex + 7), state);
    }

    /**
     * 逐键可变过期：按条目自身的最晚到期时刻 + 宽限。
     */
    private static final class StateExpiry implements Expiry<String, OutageState> {

        private final Clock clock;
        private final long graceMillis;

        private StateExpiry(Clock clock, long graceMillis) {
            this.clock = clock;
            this.graceMillis = graceMillis;
        }

        @Override
        public long expireAfterCreate(String key, OutageState value, long currentTime) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterUpdate(String key, OutageState value, long currentTime, long currentDuration) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterRead(String key, OutageState value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private long remainingNanos(OutageState state) {
            long remainingMillis = state.expireAtMillis(graceMillis) - clock.millis();
            if (remainingMillis <= 0L) {
                return TimeUnit.MILLISECONDS.toNanos(1L);
            }
            return TimeUnit.MILLISECONDS.toNanos(remainingMillis);
        }
    }
}
