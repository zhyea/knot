package org.chobit.knot.gateway.plugin.moderation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 词库快照缓存：全量词条 → 编译一次自动机 → Caffeine 缓存。
 *
 * <p><b>失效机制（2026-10-14，P0-B）</b>：请求路径上节流查一次 {@link ModerationWordSource#currentVersion()}，
 * 与缓存快照的 version 不等就失效重载。轮询而非广播，是因为 admin 与 gateway 是两个进程、
 * 缓存各自独立——版本号即失效机制天然多节点一致，不需要网关暴露管理端点、也不需要 Redis
 * （设计文档 §6 既定思路，本期落地）。代价：词库变更最迟
 * {@link ModerationConstants#VERSION_CHECK_INTERVAL_MILLIS} 毫秒后生效。</p>
 *
 * <p>TTL 10 分钟只是兜底：版本查询持续失败时仍会按期重载，不会把旧词库用一辈子。</p>
 */
public class ModerationSnapshotCache {

    private static final Logger log = LoggerFactory.getLogger(ModerationSnapshotCache.class);

    private static final int CACHE_KEY = 0;

    private final ModerationWordSource source;
    private final long versionCheckIntervalMillis;
    private final Cache<Integer, ModerationSnapshot> cache;

    /** 上次版本检查时刻；尽力而为的节流，多线程重复查询无害，故不必严格同步 */
    private volatile long lastVersionCheckMillis;

    /**
     * Constructs a new instance with the default version probe interval.
     */
    public ModerationSnapshotCache(ModerationWordSource source) {
        this(source, ModerationConstants.VERSION_CHECK_INTERVAL_MILLIS);
    }

    /**
     * Constructs a new instance with a custom version probe interval.
     *
     * @param versionCheckIntervalMillis 版本检查间隔；0 表示每次都查（测试用）
     */
    public ModerationSnapshotCache(ModerationWordSource source, long versionCheckIntervalMillis) {
        this.source = source;
        this.versionCheckIntervalMillis = Math.max(0L, versionCheckIntervalMillis);
        this.cache = Caffeine.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(ModerationConstants.WORD_CACHE_TTL)
                .build();
    }

    /**
     * 当前词库快照；词库为空时返回空快照（调用方短路）。
     */
    public ModerationSnapshot snapshot() {
        ModerationSnapshot cached = cache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            invalidateIfWordListChanged(cached);
            ModerationSnapshot refreshed = cache.getIfPresent(CACHE_KEY);
            if (refreshed != null) {
                return refreshed;
            }
        }
        return cache.get(CACHE_KEY, key -> load());
    }

    /**
     * 手动失效：供管理端保存动作直接调用（本节点立即生效，其余节点等下次版本轮询）。
     */
    public void invalidate() {
        cache.invalidateAll();
    }

    /**
     * 距上次检查够久才查版本；版本查询失败时保留现有快照（fail-open）。
     */
    private void invalidateIfWordListChanged(ModerationSnapshot cached) {
        long now = System.currentTimeMillis();
        if (now - lastVersionCheckMillis < versionCheckIntervalMillis) {
            return;
        }
        lastVersionCheckMillis = now;
        long current;
        try {
            current = source.currentVersion();
        } catch (RuntimeException e) {
            log.warn("Moderation word version probe failed, keep current snapshot: {}", e.getMessage());
            return;
        }
        if (current == cached.version()) {
            return;
        }
        log.info("Moderation word list changed, invalidating snapshot: version={} -> {}", cached.version(), current);
        cache.invalidate(CACHE_KEY);
    }

    private ModerationSnapshot load() {
        List<ModerationTerm> terms = source.loadTerms();
        return ModerationSnapshot.of(terms == null ? List.of() : terms);
    }
}
