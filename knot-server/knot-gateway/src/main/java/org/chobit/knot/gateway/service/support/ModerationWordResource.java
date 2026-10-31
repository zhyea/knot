package org.chobit.knot.gateway.service.support;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.moderation.ModerationTerm;
import org.chobit.knot.svc.service.ModerationWordService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关侧词库资源缓存：全量启用词条的单一供给点（方案 B，robin 2026-10-14 拍板）。
 *
 * <p>背景：plugin 化重构后网关曾退化为「每次插件分发直查 DB」（一个请求三个 stage
 * 各查一次，万级词库下每请求约 3MB 结果集，DB 压力随 QPS 线性涨）。本组件把词库
 * 列表按路由数据的同款节奏缓存下来，DB 查询降至最多每 30s 一次；缓存 / 编译 / 刷新
 * 的核心语义仍留在 knot-plugin 的 {@code ModerationWordCache}（编译自动机按
 * {@link ModerationTerm#versionOf(List)} 去重）——两边分工：网关缓存「词库资源」，
 * 插件缓存「编译产物」。</p>
 *
 * <p>时序语义（与 {@code GatewayConfigCacheService} 路由缓存一致）：写入 30s 后异步刷新、
 * 10min 硬过期。词库变更生效延迟约 30s；Caffeine 刷新期间旧值持续可见，不出现取词
 * 空窗。同步加载失败不缓存、逐次重试。</p>
 *
 * <p>fail-open：取词异常（含 DB 不可用）不冒泡到请求链路，降级为空列表并 warn——
 * 守卫见空词表即放行，与重构前行为一致。</p>
 */
@Slf4j
@Component
public class ModerationWordResource {

    /** 单例资源：词库不分键，全量启用列表即整份资源 */
    private static final String ENABLED_KEY = "enabled";

    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);
    private static final Duration REFRESH_AFTER_WRITE = Duration.ofSeconds(30);

    private final LoadingCache<String, List<ModerationTerm>> cache;

    /** fail-open 累计次数：非 0 即意味着有请求是在「无敏感词过滤」状态下被放行的 */
    private final AtomicLong failOpenCount = new AtomicLong();

    /** 持续 fail-open 的起始毫秒时间戳；0 表示当前正常 */
    private volatile long failOpenSinceMillis;

    /** 最近一次成功取词的词库版本与规模：fail-open 期间保持上一次的值，便于判断陈旧程度 */
    private volatile String lastVersion;
    private volatile int lastWordCount;

    /**
     * Constructs a new instance.
     */
    public ModerationWordResource(ModerationWordService moderationWordService) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .refreshAfterWrite(REFRESH_AFTER_WRITE)
                .build(key -> {
                    List<ModerationTerm> terms = moderationWordService.loadEnabledTerms();
                    return terms == null ? List.of() : List.copyOf(terms);
                });
    }

    /**
     * 当前全量启用词条（网关资源供给）；不可用时 fail-open 成空列表（守卫见空即放行）。
     */
    public List<ModerationTerm> currentWords() {
        try {
            List<ModerationTerm> terms = cache.get(ENABLED_KEY);
            failOpenSinceMillis = 0L;
            if (terms != null) {
                lastWordCount = terms.size();
                lastVersion = String.valueOf(ModerationTerm.versionOf(terms));
            }
            return terms;
        } catch (RuntimeException e) {
            // fail-open 语义不变（40-plugin.md 已登记），但必须可计量：
            // 只在日志里可见 = 内容安全能力被摘除而无人知晓。
            long total = failOpenCount.incrementAndGet();
            if (failOpenSinceMillis == 0L) {
                failOpenSinceMillis = System.currentTimeMillis();
            }
            log.warn("Moderation word resource unavailable, fail-open with empty list "
                            + "(total={}, since={}, lastVersion={}): {}",
                    total, failOpenSinceMillis, lastVersion, e.getMessage());
            return List.of();
        }
    }

    /** fail-open 累计次数（监控面）。 */
    public long failOpenCount() {
        return failOpenCount.get();
    }

    /** 持续 fail-open 的起始毫秒时间戳（0 = 当前正常）。 */
    public long failOpenSinceMillis() {
        return failOpenSinceMillis;
    }

    /** 最近一次成功取词的词库版本；null 表示至今没成功过。 */
    public String lastVersion() {
        return lastVersion;
    }

    /** 最近一次成功取词的词条规模。 */
    public int lastWordCount() {
        return lastWordCount;
    }
}
