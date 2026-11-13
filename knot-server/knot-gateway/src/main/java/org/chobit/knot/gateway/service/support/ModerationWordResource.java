package org.chobit.knot.gateway.service.support;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.moderation.ModerationTerm;
import org.chobit.knot.gateway.metrics.GatewayMetrics;
import org.chobit.knot.svc.service.ModerationWordService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关侧词库资源缓存：全量启用词条的单一供给点；编译产物缓存仍在 knot-plugin 的 {@code ModerationWordCache}。
 * 写入 30s 后异步刷新、10min 硬过期，刷新期间旧值持续可见；同步加载失败不缓存、逐次重试。
 * fail-open：取词异常（含 DB 不可用）降级为空列表并 warn，守卫见空词表即放行。
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
     * fail-open 的指标出口：单调计数给告警用，两个 gauge 反映当前是否处于 fail-open 与最近一次词条数。
     */
    private final Counter failOpenCounter;

    public ModerationWordResource(ModerationWordService moderationWordService, MeterRegistry registry) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .refreshAfterWrite(REFRESH_AFTER_WRITE)
                .build(key -> {
                    List<ModerationTerm> terms = moderationWordService.loadEnabledTerms();
                    return terms == null ? List.of() : List.copyOf(terms);
                });
        this.failOpenCounter = Counter.builder(GatewayMetrics.MODERATION_FAIL_OPEN)
                .description("敏感词库不可用导致的 fail-open 次数（非 0 = 有请求在无过滤状态下放行）")
                .register(registry);
        // strongReference：Gauge 默认持弱引用，被回收后读数会静默变成 NaN。
        // 本类是单例 bean，引用本不该被回收，但别让这个前提成为「指标某天变 NaN」的隐因。
        Gauge.builder(GatewayMetrics.MODERATION_FAIL_OPEN_ACTIVE, this,
                        r -> r.failOpenSinceMillis() > 0L ? 1 : 0)
                .description("当前是否处于敏感词 fail-open（1 = 内容安全能力已摘除）")
                .strongReference(true)
                .register(registry);
        Gauge.builder(GatewayMetrics.MODERATION_WORD_COUNT, this, ModerationWordResource::lastWordCount)
                .description("最近一次成功取词的敏感词条数（突降 = 词库被清空或读到空表）")
                .strongReference(true)
                .register(registry);
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
            failOpenCounter.increment();
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
