package org.chobit.knot.gateway.routing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 一致性 hash 环（Ketama 口径），用于模型池内的粘性路由。
 *
 * <p>每个候选建固定数量的虚拟节点落到环上，按粘性键的 hash 顺时针取第一个虚拟节点所属候选。
 * 相较 {@code hash % n} 的好处：候选集合变化（池内增删模型、停用启用）时只影响环上相邻段，
 * 其余键的命中保持不变，把「池变更导致的 prompt cache 重击」限制在最小范围。</p>
 *
 * <p>纯函数、无 Spring 依赖：同样的候选集合在任何节点上建出的环完全一致，
 * 因此粘性路由不需要共享存储（既无绑定记录，也无需 Redis 同步）。</p>
 */
public final class ConsistentHashRing {

    /**
     * 每个候选的虚拟节点数（Ketama 经典值）。只影响分布均匀度、不影响正确性，
     * 因此不暴露为配置项。
     */
    public static final int VIRTUAL_NODES = 160;

    private static final ConsistentHashRing EMPTY = new ConsistentHashRing(new TreeMap<>());

    private final NavigableMap<Long, String> ring;

    private ConsistentHashRing(NavigableMap<Long, String> ring) {
        this.ring = ring;
    }

    /**
     * 按候选编码集合建环；空白编码跳过，空集合返回空环。
     */
    public static ConsistentHashRing of(Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return EMPTY;
        }
        NavigableMap<Long, String> map = new TreeMap<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String node = candidate.trim();
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                map.put(md5Long(node + "#" + i), node);
            }
        }
        return map.isEmpty() ? EMPTY : new ConsistentHashRing(map);
    }

    /**
     * 取粘性键顺时针方向的第一个候选；空环或空键返回 {@code null}。
     */
    public String select(String key) {
        if (ring.isEmpty() || key == null || key.isEmpty()) {
            return null;
        }
        Map.Entry<Long, String> entry = ring.ceilingEntry(md5Long(key));
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry == null ? null : entry.getValue();
    }

    /**
     * MD5 前 8 字节按大端拼成 long——Ketama 经典取位方式，JDK 自带实现，零依赖。
     *
     * <p>只要确定性与均匀度，不需要抗碰撞强度；MD5 在所有 JDK 中均可用。</p>
     */
    public static long md5Long(String value) {
        byte[] digest = digest(value);
        long result = 0L;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (digest[i] & 0xffL);
        }
        return result;
    }

    /**
     * 键的短摘要，仅供日志/链路追踪使用（避免把客户端原始粘性键写进日志）。
     */
    public static String digestShort(String value) {
        return Long.toHexString(md5Long(value));
    }

    private static byte[] digest(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // MD5 是 JDK 强制实现，走到这里说明运行环境异常，直接升级为不可恢复错误
            throw new IllegalStateException("MD5 digest unavailable", e);
        }
    }
}
