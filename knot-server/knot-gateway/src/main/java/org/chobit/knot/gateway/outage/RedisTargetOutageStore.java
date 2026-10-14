package org.chobit.knot.gateway.outage;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.model.EjectionPolicy;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多节点摘除状态存储：Redis Hash + Lua。
 *
 * <p>状态存为 Hash（字段 {@code f/s/w/e/p/c/r}）而非 JSON 串：Lua 5.1 的数字是双精度，
 * 毫秒时间戳经 cjson 编码有被写成科学计数法的风险，而 Hash 字段是纯字符串，
 * 由 Java 侧 {@code Long.parseLong} 精确还原。</p>
 *
 * <p>每个操作都是单段 Lua：多节点并发下的「读-改-写」必须原子，且 {@code HSET} 与
 * {@code PEXPIREAT} 必须在同一脚本里——拆成两条命令时进程在中间崩掉会留下永不过期的键
 * （该教训已写在 {@code RedisModelLoadStore} 注释里）。</p>
 *
 * <p>启用方式：{@code knot.outage.store=redis}（并配好 {@code spring.data.redis.*}）。</p>
 */
@Slf4j
public class RedisTargetOutageStore implements TargetOutageStore {

    private static final String FIELD_FAILURES = "f";
    private static final String FIELD_SLOWS = "s";
    private static final String FIELD_WINDOW_END = "w";
    private static final String FIELD_EJECTED_UNTIL = "e";
    private static final String FIELD_PROBE_UNTIL = "p";
    private static final String FIELD_COOLDOWN = "c";
    private static final String FIELD_REASON = "r";

    /**
     * 记录一次异常：窗口判定 → 计数 → 达阈值摘除 / 探活失败加成冷却。
     *
     * <p>ARGV：now, windowMs, threshold, cooldownMs, maxCooldownMs, multiplierTenths, slowFlag, graceMs</p>
     */
    private static final RedisScript<List> ANOMALY = RedisScript.of("""
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local threshold = tonumber(ARGV[3])
            local cooldownMs = tonumber(ARGV[4])
            local maxCooldownMs = tonumber(ARGV[5])
            local multiplierTenths = tonumber(ARGV[6])
            local slow = ARGV[7] == '1'
            local graceMs = tonumber(ARGV[8])

            local raw = redis.call('HGETALL', KEYS[1])
            local st = {f = 0, s = 0, w = 0, e = 0, p = 0, c = 0, r = ''}
            for i = 1, #raw, 2 do
                local field = raw[i]
                local value = raw[i + 1]
                if field == 'r' then
                    st.r = value
                else
                    st[field] = tonumber(value) or 0
                end
            end

            if st.w <= now then
                st.f = 0
                st.s = 0
                st.w = now + windowMs
            end
            if slow then st.s = st.s + 1 else st.f = st.f + 1 end
            local reason = 'FAILURE'
            if slow then reason = 'SLOW' end

            if st.p > now then
                local base = st.c
                if base <= 0 then base = cooldownMs end
                local grown = math.ceil(base * multiplierTenths / 10)
                if grown < base then grown = base end
                if grown > maxCooldownMs then grown = maxCooldownMs end
                st.e = now + grown
                st.p = 0
                st.c = grown
                st.r = reason
            elseif st.e > now then
                local extended = now + cooldownMs
                if extended > st.e then st.e = extended end
            elseif (st.f + st.s) >= threshold then
                st.e = now + cooldownMs
                st.c = cooldownMs
                st.r = reason
            end

            redis.call('HSET', KEYS[1], 'f', st.f, 's', st.s, 'w', st.w,
                       'e', st.e, 'p', st.p, 'c', st.c, 'r', st.r)
            local latest = math.max(st.w, math.max(st.e, st.p))
            redis.call('PEXPIREAT', KEYS[1], latest + graceMs)
            return {tostring(st.f), tostring(st.s), tostring(st.w),
                    tostring(st.e), tostring(st.p), tostring(st.c), st.r}
            """, List.class);

    /**
     * 记录成功：探活成功整条删除；普通成功清零窗口计数（不动摘除状态）。
     *
     * <p>ARGV：now, probingFlag</p>
     */
    private static final RedisScript<Long> SUCCESS = RedisScript.of("""
            if ARGV[2] == '1' then
                return redis.call('DEL', KEYS[1])
            end
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('HSET', KEYS[1], 'f', 0, 's', 0)
            return 1
            """, Long.class);

    /**
     * 抢占探活租约：冷却已过且无租约在途才放行，返回 1 表示抢占成功。
     *
     * <p>ARGV：now, probeTimeoutMs</p>
     */
    private static final RedisScript<Long> PROBE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            local now = tonumber(ARGV[1])
            if (tonumber(redis.call('HGET', KEYS[1], 'e')) or 0) > now then
                return 0
            end
            if (tonumber(redis.call('HGET', KEYS[1], 'p')) or 0) > now then
                return 0
            end
            redis.call('HSET', KEYS[1], 'p', now + tonumber(ARGV[2]))
            return 1
            """, Long.class);

    /**
     * 手动摘除：直接进入冷却，不依赖窗口计数。
     *
     * <p>ARGV：now, cooldownMs, graceMs</p>
     */
    private static final RedisScript<Long> FORCE_EJECT = RedisScript.of("""
            local now = tonumber(ARGV[1])
            local cooldownMs = tonumber(ARGV[2])
            local until = now + cooldownMs
            redis.call('HSET', KEYS[1], 'f', 0, 's', 0, 'w', until,
                       'e', until, 'p', 0, 'c', cooldownMs, 'r', 'MANUAL')
            redis.call('PEXPIREAT', KEYS[1], until + tonumber(ARGV[3]))
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final long graceMillis;
    private final boolean failOpen;

    /**
     * Constructs a new instance.
     *
     * @param redis    字符串模板（状态以 Hash 字段存放，不需要序列化器）
     * @param graceMillis 到期后额外保留的宽限（毫秒）
     * @param failOpen Redis 不可用时是否放行：{@code true} 按「健康」处理，不摘除
     */
    public RedisTargetOutageStore(StringRedisTemplate redis, long graceMillis, boolean failOpen) {
        this.redis = redis;
        this.graceMillis = graceMillis;
        this.failOpen = failOpen;
    }

    @Override
    public String id() {
        return "redis";
    }

    @Override
    public OutageState state(String key, long nowMillis) {
        try {
            Map<Object, Object> raw = redis.opsForHash().entries(key);
            return raw == null || raw.isEmpty() ? OutageState.HEALTHY : toState(raw);
        } catch (RuntimeException ex) {
            return onFailure("state", key, ex, OutageState.HEALTHY);
        }
    }

    @Override
    public OutageState recordAnomaly(String key, EjectionPolicy policy, AnomalyKind kind, long nowMillis) {
        try {
            List result = redis.execute(ANOMALY, List.of(key),
                    Long.toString(nowMillis),
                    Long.toString(policy.windowMillis()),
                    Integer.toString(policy.threshold()),
                    Long.toString(policy.cooldownMs()),
                    Long.toString(policy.maxCooldownMs()),
                    Long.toString(Math.round(policy.cooldownMultiplier() * 10.0)),
                    AnomalyKind.SLOW == kind ? "1" : "0",
                    Long.toString(graceMillis));
            return fromScriptResult(result);
        } catch (RuntimeException ex) {
            return onFailure("recordAnomaly", key, ex, OutageState.HEALTHY);
        }
    }

    @Override
    public void recordSuccess(String key, boolean probing, long nowMillis) {
        try {
            redis.execute(SUCCESS, List.of(key), Long.toString(nowMillis), probing ? "1" : "0");
        } catch (RuntimeException ex) {
            onFailure("recordSuccess", key, ex, null);
        }
    }

    @Override
    public boolean tryAcquireProbe(String key, long probeTimeoutMs, long nowMillis) {
        try {
            Long acquired = redis.execute(PROBE, List.of(key), Long.toString(nowMillis), Long.toString(probeTimeoutMs));
            return acquired != null && acquired > 0L;
        } catch (RuntimeException ex) {
            return onFailure("tryAcquireProbe", key, ex, false);
        }
    }

    @Override
    public void release(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ex) {
            onFailure("release", key, ex, null);
        }
    }

    @Override
    public void forceEject(String key, long cooldownMs, long nowMillis) {
        try {
            redis.execute(FORCE_EJECT, List.of(key), Long.toString(nowMillis), Long.toString(cooldownMs),
                    Long.toString(graceMillis));
        } catch (RuntimeException ex) {
            onFailure("forceEject", key, ex, null);
        }
    }

    @Override
    public List<OutageEntry> list(long nowMillis) {
        List<OutageEntry> result = new ArrayList<>();
        try {
            redis.execute((RedisCallback<List<OutageEntry>>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().count(200).build();
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        String key = new String(cursor.next(), StandardCharsets.UTF_8);
                        if (!key.contains(":RULE:")) {
                            continue;
                        }
                        Map<byte[], byte[]> hash = connection.hashCommands().hGetAll(key.getBytes(StandardCharsets.UTF_8));
                        if (hash == null || hash.isEmpty()) {
                            continue;
                        }
                        Map<Object, Object> values = new HashMap<>();
                        for (Map.Entry<byte[], byte[]> entry : hash.entrySet()) {
                            values.put(new String(entry.getKey(), StandardCharsets.UTF_8),
                                    new String(entry.getValue(), StandardCharsets.UTF_8));
                        }
                        OutageState state = toState(values);
                        if (!state.ejected(nowMillis) && !state.probing(nowMillis)) {
                            continue;
                        }
                        OutageEntry parsed = parseEntry(key, state);
                        if (parsed != null) {
                            result.add(parsed);
                        }
                    }
                }
                return result;
            });
        } catch (RuntimeException ex) {
            log.warn("Outage store unavailable while listing, fail-open: {}", ex.getMessage());
            return failOpen ? List.of() : result;
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

    private static OutageState toState(Map<Object, Object> raw) {
        Map<String, String> values = new HashMap<>();
        raw.forEach((field, value) -> values.put(String.valueOf(field), String.valueOf(value)));
        return new OutageState(
                (int) parseLong(values.get(FIELD_FAILURES)),
                (int) parseLong(values.get(FIELD_SLOWS)),
                parseLong(values.get(FIELD_WINDOW_END)),
                parseLong(values.get(FIELD_EJECTED_UNTIL)),
                parseLong(values.get(FIELD_PROBE_UNTIL)),
                parseLong(values.get(FIELD_COOLDOWN)),
                values.getOrDefault(FIELD_REASON, "")
        );
    }

    private OutageState fromScriptResult(List result) {
        if (result == null || result.size() < 7) {
            return OutageState.HEALTHY;
        }
        String reason = String.valueOf(result.get(6));
        return new OutageState(
                (int) parseLong(String.valueOf(result.get(0))),
                (int) parseLong(String.valueOf(result.get(1))),
                parseLong(String.valueOf(result.get(2))),
                parseLong(String.valueOf(result.get(3))),
                parseLong(String.valueOf(result.get(4))),
                parseLong(String.valueOf(result.get(5))),
                "nil".equals(reason) ? "" : reason
        );
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * Redis 故障时的处置：默认 fail-open（按「健康」/「不抢占」处理），
     * 避免缓存层故障直接打挂网关选路。
     */
    private <T> T onFailure(String operation, String key, RuntimeException ex, T fallback) {
        if (failOpen) {
            log.warn("Outage store unavailable, fail-open on {}: key={}, {}", operation, key, ex.getMessage());
            return fallback;
        }
        throw ex;
    }
}
