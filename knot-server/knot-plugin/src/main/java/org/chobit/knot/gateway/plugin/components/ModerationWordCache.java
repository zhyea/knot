package org.chobit.knot.gateway.plugin.components;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.moderation.ModerationTerm;
import org.chobit.knot.gateway.plugin.moderation.ModerationSnapshot;
import org.chobit.knot.gateway.plugin.moderation.PluginProperties;
import org.chobit.knot.gateway.plugin.schedule.Refreshable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 词库编译缓存（knot-plugin 组件）：持有网关经插件上下文每请求下发的最新词表，
 * 按<b>内容版本</b>（{@link ModerationTerm#versionOf}）去重编译 Aho-Corasick 自动机，
 * 守卫经 Spring IOC 取用，稳态下零重复编译。
 *
 * <p>拓扑（2026-10-14 robin 定调「弱化 Moderation 在 gateway 的体现，核心逻辑进插件，
 * gateway 只提供资源」）：词库数据的存取与<b>列表缓存</b>留在网关侧
 * （{@code ModerationWordResource}，按路由数据同款节奏约 30s 刷新），网关每请求把
 * 全量启用词条随 {@code GatewayPluginContext.words} 下发——这是网关唯一的审核职责
 * （资源供给）；<b>编译</b>、编译产物缓存、定时重建全部收敛到本组件。</p>
 *
 * <p>两边分工：网关缓存「词库资源列表」（DB 查询最多每 30s 一次），本组件缓存
 * 「编译自动机」——网关给的是缓存内的稳定实例，内容未变时 {@code versionOf} 一致，
 * 稳态零重复编译。</p>
 *
 * <p><b>刷新机制</b>：</p>
 * <ul>
 *   <li><b>内容版本去重（主路径）</b>：网关每请求下发新列表实例，但词条内容未变时
 *       {@code versionOf}（启用未删行 {@code MAX(updated_at)} 同口径）不变，直接复用已编译快照；
 *       内容变化才重编译。相比旧的「实例标识比对」，对每请求新实例同样有效。</li>
 *   <li><b>定时防御性重建（@Scheduled 兜底）</b>：按 {@code knot.plugin.moderation.refresh-interval}
 *       （默认 10s）用最近一次收到的词表重编译一遍，保证编译产物始终可由最新已知词表导出，
 *       不依赖任何版本信号（旧拓扑里这是覆盖 max(updated_at) 盲区的主路径；新拓扑下网关
 *       每请求全量下发已无盲区，此处保留为兜底）。</li>
 * </ul>
 *
 * <p>故障模式 fail-open：词表为空 / null 时返回空快照（守卫见空即放行）。</p>
 */
@Slf4j
@Component
public class ModerationWordCache implements Refreshable {

    private static final List<ModerationTerm> EMPTY_WORDS = List.of();
    private static final ModerationSnapshot EMPTY_SNAPSHOT = ModerationSnapshot.of(EMPTY_WORDS);

    private final PluginProperties properties;

    /** 最近一次参与编译的词表，供定时刷新重建使用 */
    private volatile List<ModerationTerm> lastWords = EMPTY_WORDS;

    /**
     * 已编译快照与其内容版本。写序固定为「先快照后版本」，
     * 读者见版本命中即可安全读到对应快照（volatile happens-before）。
     */
    private volatile ModerationSnapshot snapshot = EMPTY_SNAPSHOT;
    private volatile long compiledVersion = 0L;

    /**
     * Constructs a new instance.
     */
    public ModerationWordCache(PluginProperties properties) {
        this.properties = properties;
    }

    /**
     * 取当前词表对应的编译快照：内容版本与已编译版本一致则复用，否则编译新快照。
     * 空 / null 词表直接给空快照（fail-open，不冲刷已有编译产物）。
     */
    public ModerationSnapshot snapshotFor(List<ModerationTerm> words) {
        if (words == null || words.isEmpty()) {
            return EMPTY_SNAPSHOT;
        }
        long version = ModerationTerm.versionOf(words);
        if (version == compiledVersion) {
            ModerationSnapshot current = snapshot;
            if (current != null && !current.empty()) {
                return current;
            }
        }
        return compile(words, version);
    }

    /**
     * 定时刷新入口（{@code @Scheduled} 驱动）：用最近一次收到的词表防御性重编译。
     */
    @Scheduled(fixedDelayString = "${knot.plugin.moderation.refresh-interval:10000}")
    @Override
    public void refresh() {
        List<ModerationTerm> current = lastWords;
        if (current == null || current.isEmpty()) {
            return;
        }
        compile(current, ModerationTerm.versionOf(current));
        log.debug("Moderation word cache defensively rebuilt: version={}, words={}",
                compiledVersion, current.size());
    }

    /**
     * 定时刷新间隔（声明值，实际调度由 {@code @Scheduled} 注解承担）。
     */
    @Override
    public Duration refreshInterval() {
        return properties.getModeration().getRefreshInterval();
    }

    private ModerationSnapshot compile(List<ModerationTerm> words, long version) {
        ModerationSnapshot fresh = ModerationSnapshot.of(words);
        this.snapshot = fresh;
        this.compiledVersion = version;
        this.lastWords = words;
        return fresh;
    }
}
