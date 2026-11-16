package org.chobit.knot.gateway.service.support;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.config.ConfigCacheDomain;
import org.chobit.knot.gateway.config.ConfigRefreshProperties;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.svc.model.ConfigTableRevision;
import org.chobit.knot.svc.service.support.ConfigRevisionQuerySupport;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 配置缓存主动刷新：按节拍比对配置表指纹，差异域整域失效。
 *
 * <p>探测走拉模式而不是等管理端通知：admin 与 gateway 是独立进程，且管理端没有任何
 * 广播通道；拉模式天然不丢变更，节点重启也能自动对齐。指纹由
 * {@link ConfigRevisionQuerySupport} 一条 SQL 全量返回。</p>
 *
 * <p><b>失败口径</b>：探测异常 → 只记 ERROR、保留现有缓存，等下一拍。配置缓存是只读副本，
 * 不一致的后果是「生效晚」，不是「算错」，因此不套 Redis 的 fail-hard / 503。
 * 首个节拍只建立基线、不失效——新进程缓存本来就是空的。</p>
 */
@Slf4j
@Component
public class ConfigCacheRefreshRunner {

    private final ConfigRevisionQuerySupport revisionQuery;
    private final GatewayConfigCacheService configCache;
    private final ModerationWordResource moderationWordResource;
    private final ConfigRefreshProperties properties;

    /** 快照里缺席的表的哨兵指纹。 */
    private static final String MISSING_TABLE = "__missing__";

    /** 上一拍的表 → 指纹；volatile 而非加锁：只有本 Runner 写，读到的旧值最多多失效一次。 */
    private volatile Map<String, String> lastFingerprints = Map.of();

    /** 已报过错的缺失表，避免每拍刷同一批 ERROR。 */
    private final Set<String> reportedMissingTables = ConcurrentHashMap.newKeySet();

    public ConfigCacheRefreshRunner(ConfigRevisionQuerySupport revisionQuery,
                                    GatewayConfigCacheService configCache,
                                    ModerationWordResource moderationWordResource,
                                    ConfigRefreshProperties properties) {
        this.revisionQuery = revisionQuery;
        this.configCache = configCache;
        this.moderationWordResource = moderationWordResource;
        this.properties = properties;
    }

    /**
     * 一次探测，由 {@link org.chobit.knot.gateway.schedule.GatewayScheduledEntryRunner} 按节拍触发。
     * 异常不外抛：节拍方法抛出会让后续节拍继续跑，
     * 但日志会丢失「本轮未生效」的语义，因此在这里收敛为 ERROR + 保留旧缓存。
     */
    public void tick() {
        if (!properties.isEnabled()) {
            return;
        }
        Map<String, String> current;
        try {
            current = revisionQuery.snapshot().stream()
                    .collect(Collectors.toMap(
                            ConfigTableRevision::tableName,
                            revision -> revision.rowCount() + "@" + revision.revision(),
                            (left, right) -> left));
        } catch (Exception e) {
            log.error("Config cache revision probe failed; keeping current cache until next tick", e);
            return;
        }

        if (current.isEmpty()) {
            log.warn("Config cache revision snapshot is empty; skipping this tick");
            return;
        }

        Map<String, String> previous = lastFingerprints;
        lastFingerprints = current;
        if (previous.isEmpty()) {
            log.info("Config cache revision baseline established: {} tables", current.size());
            return;
        }

        Set<ConfigCacheDomain> changed = detectChanges(previous, current);
        if (!changed.isEmpty()) {
            invalidate(changed);
        }
    }

    private Set<ConfigCacheDomain> detectChanges(Map<String, String> previous, Map<String, String> current) {
        Set<ConfigCacheDomain> changed = EnumSet.noneOf(ConfigCacheDomain.class);
        for (ConfigCacheDomain domain : ConfigCacheDomain.values()) {
            for (String table : domain.tables()) {
                // 缺失表用哨兵值参与比较：一直缺 → 不反复失效（否则每拍全量清空）；
                // 从有到缺 / 从缺到有 → 各失效一次。
                String now = current.getOrDefault(table, MISSING_TABLE);
                String before = previous.getOrDefault(table, MISSING_TABLE);
                if (!now.equals(before)) {
                    changed.add(domain);
                }
                if (MISSING_TABLE.equals(now) && reportedMissingTables.add(table)) {
                    log.error("Config revision snapshot misses table '{}' (domain {}); "
                            + "that domain can only be refreshed by TTL", table, domain);
                }
            }
        }
        return changed;
    }

    private void invalidate(Set<ConfigCacheDomain> domains) {
        Set<ConfigCacheDomain> shared = EnumSet.copyOf(domains);
        shared.remove(ConfigCacheDomain.MODERATION_WORD);
        if (!shared.isEmpty()) {
            configCache.invalidate(shared);
        }
        if (domains.contains(ConfigCacheDomain.MODERATION_WORD)) {
            moderationWordResource.invalidate();
        }
        log.info("Config cache invalidated for domains {}", domains);
    }
}
