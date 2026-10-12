package org.chobit.knot.gateway.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.model.StickyPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 模型池内的粘性路由：按请求头携带的粘性键做一致性 hash，给出池内首选模型。
 *
 * <p>只回答「首选是谁」——命中目标由调用方置顶到候选首位，其余候选仍按池选择策略原序
 * 作为 failover 链。因此粘性不改变故障转移行为，也不感知目标健康度：
 * 首选被频控拒绝或上游失败时，顺延下一个候选，请求不会因粘性而失败。</p>
 *
 * <p><b>无状态</b>：不记录绑定、没有 TTL、没有失效逻辑。粘性结果由 {@code (候选集合, 粘性键)}
 * 确定性算出，多节点各自计算必然一致，故不需要 Redis 之类的共享存储。
 * 唯一被缓存的是 hash 环本身（160 虚拟节点 × 候选数的 MD5 开销在请求热路径上不可忽略），
 * 且它是纯函数结果，用进程内 Caffeine 即可，跨节点共享没有任何收益。</p>
 */
@Slf4j
@Component
public class StickyRouting {

    /** 粘性键长度上限：超长视为无效输入，跳过粘性（防异常头值滥用） */
    public static final int MAX_KEY_LENGTH = 512;

    private static final int RING_CACHE_MAXIMUM_SIZE = 512;

    /** 与 GatewayDataService 的池数据缓存同周期，保证环不会比池成员更「新」 */
    private static final Duration RING_CACHE_TTL = Duration.ofMinutes(10);

    /** 候选集合 → hash 环；环是纯函数结果，缓存只为省掉每请求的建环开销 */
    private final Cache<String, ConsistentHashRing> rings = Caffeine.newBuilder()
            .maximumSize(RING_CACHE_MAXIMUM_SIZE)
            .expireAfterWrite(RING_CACHE_TTL)
            .build();

    /**
     * 归一化粘性键：空白或超长返回 {@code null}（表示本次不启用粘性）。
     */
    public String normalizeKey(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_KEY_LENGTH) {
            log.warn("Sticky key rejected: too long (limit={}, digest={})",
                    MAX_KEY_LENGTH, ConsistentHashRing.digestShort(trimmed));
            return null;
        }
        return trimmed;
    }

    /**
     * 按策略与粘性键给出池内首选模型编码。
     *
     * @param candidates 池内可用候选编码（顺序无关，仅用于建环）
     * @param policy     池级粘性策略，null 按 {@link StickyPolicy#DEFAULT} 处理（默认关闭）
     * @param stickyKey  原始粘性键（请求头原值），为空/超长时返回 null
     * @return 首选模型编码；不适用粘性时返回 {@code null}，调用方应保留原候选顺序
     */
    public String preferred(Collection<String> candidates, StickyPolicy policy, String stickyKey) {
        StickyPolicy effective = policy == null ? StickyPolicy.DEFAULT : policy;
        if (!effective.stickied()) {
            return null;
        }
        String key = normalizeKey(stickyKey);
        if (key == null || candidates == null || candidates.size() < 2) {
            return null;
        }
        ConsistentHashRing ring = rings.get(ringKey(candidates), ignored -> ConsistentHashRing.of(candidates));
        return ring.select(key);
    }

    /**
     * 环缓存键 = 排序后的候选集合。排序保证「同一集合、不同顺序」命中同一个环，
     * 且不同池若成员完全相同也能复用（同样的集合建出的环必然一致）。
     */
    private static String ringKey(Collection<String> candidates) {
        List<String> sorted = new ArrayList<>(candidates.size());
        for (String candidate : candidates) {
            if (candidate != null) {
                sorted.add(candidate);
            }
        }
        Collections.sort(sorted);
        return String.join(",", sorted);
    }
}
