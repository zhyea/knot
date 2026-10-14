package org.chobit.knot.gateway.plugin.moderation;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.chobit.knot.gateway.plugin.refresh.Refreshable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * 词库快照缓存：全量词条 → 编译一次自动机 → Caffeine 缓存。
 *
 * <p><b>失效机制（2026-10-14 复盘后修订，P0-B 修复）</b>：三层叠加，确保任意写类型都在可控延迟内生效。</p>
 * <ol>
 *   <li><b>版本号轮询（快速路径）</b>：命中请求路径上节流查一次 {@link ModerationWordSource#currentVersion()}
 *       （{@code MAX(updated_at)}），与缓存快照 version 不等就失效重载。对「编辑最新行」这类版本敏感改动即时生效。</li>
 *   <li><b>定时刷新（盲区主修复）</b>：本类实现 {@link Refreshable}，由
 *       {@link org.chobit.knot.gateway.plugin.refresh.ScheduledRefreshManager} 每 {@code refreshInterval}（默认 10s）
 *       调度 {@link #refresh()} → {@code cache.refresh()} 直读当前真实词表（不依赖版本号比对），覆盖<b>所有</b>写类型
 *       （新增/编辑/删除/停用/启用），是版本信号结构性盲区（删除/停用非最新行）的确定性修复。</li>
 *   <li><b>refreshAfterWrite（调度失效兜底）</b>：访问到期（1min）后异步重载。即便定时刷新线程异常，
 *       单条请求也不会把旧词库用一辈子。</li>
 * </ol>
 *
 * <p>三层都不依赖 admin/gateway 间广播：两进程缓存各自独立，版本号轮询天然多节点一致；
 * 定时刷新与 refreshAfterWrite 是各节点自转的兜底，无需网关暴露管理端点、无需 Redis。</p>
 *
 * <p>TTL 10 分钟仍是 idle 兜底：任何重载路径持续失败时，到期也强制重载。</p>
 *
 * <p>故障模式 fail-open：词库加载失败时保留现有快照（{@link ModerationGuard} 进一步兜底放行并告警）。</p>
 */
public class ModerationSnapshotCache implements Refreshable {

    private static final Logger log = LoggerFactory.getLogger(ModerationSnapshotCache.class);

    private static final int CACHE_KEY = 0;

    private final ModerationWordSource source;
    private final long versionCheckIntervalMillis;
    private final Duration refreshInterval;
    private final LoadingCache<Integer, ModerationSnapshot> cache;

    /** 上次版本检查时刻；尽力而为的节流，多线程重复查询无害，故不必严格同步 */
    private volatile long lastVersionCheckMillis;

    /**
     * 默认版本轮询间隔 + 默认定时刷新间隔。
     */
    public ModerationSnapshotCache(ModerationWordSource source) {
        this(source, ModerationConstants.VERSION_CHECK_INTERVAL_MILLIS, ModerationConstants.SCHEDULED_REFRESH_INTERVAL);
    }

    /**
     * 自定义版本轮询间隔 + 默认定时刷新间隔。
     *
     * @param versionCheckIntervalMillis 版本检查间隔；0 表示每次都查（测试用）
     */
    public ModerationSnapshotCache(ModerationWordSource source, long versionCheckIntervalMillis) {
        this(source, versionCheckIntervalMillis, ModerationConstants.SCHEDULED_REFRESH_INTERVAL);
    }

    /**
     * 全参构造器。
     *
     * @param versionCheckIntervalMillis 版本检查间隔；0 表示每次都查（测试用）
     * @param refreshInterval            定时刷新间隔（绕过版本盲区的主路径）；必须 &gt; 0
     */
    public ModerationSnapshotCache(ModerationWordSource source,
                                   long versionCheckIntervalMillis,
                                   Duration refreshInterval) {
        this.source = source;
        this.versionCheckIntervalMillis = Math.max(0L, versionCheckIntervalMillis);
        this.refreshInterval = refreshInterval == null || refreshInterval.toMillis() <= 0
                ? ModerationConstants.SCHEDULED_REFRESH_INTERVAL
                : refreshInterval;
        this.cache = Caffeine.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(ModerationConstants.WORD_CACHE_TTL)
                .refreshAfterWrite(ModerationConstants.REFRESH_AFTER_WRITE)
                .build(key -> load());
    }

    /**
     * 定时刷新入口（由 {@link org.chobit.knot.gateway.plugin.refresh.ScheduledRefreshManager} 调度）：
     * 直读当前真实词表，绕开 max(updated_at) 版本盲区。非阻塞——Caffeine 异步重载，旧值保留至新值就绪。
     */
    @Override
    public void refresh() {
        cache.refresh(CACHE_KEY);
    }

    /**
     * 定时刷新间隔（托管器据此调度）。
     */
    @Override
    public Duration refreshInterval() {
        return refreshInterval;
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
        return cache.get(CACHE_KEY);
    }

    /**
     * 手动失效：供管理端保存动作直接调用（本节点立即生效，其余节点等下次版本轮询 / 定时刷新）。
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
