package org.chobit.knot.gateway.plugin.components;

import org.chobit.knot.gateway.moderation.ModerationTerm;
import org.chobit.knot.gateway.plugin.moderation.ModerationSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 词库编译缓存（knot-plugin 组件）：把网关经插件上下文每请求下发的词表编译为
 * Aho-Corasick 自动机，按<b>内容版本</b>（{@link ModerationTerm#versionOf}）去重，
 * 守卫经 Spring IOC 取用，稳态下零重复编译。
 *
 * <p>拓扑（2026-10-14 robin 定调「弱化 Moderation 在 gateway 的体现，核心逻辑进插件，
 * gateway 只提供资源」）：词库列表的存取与缓存在网关侧
 * （{@code ModerationWordResource}，按路由数据同款节奏约 30s 刷新），本组件只做
 * 「编译 + 版本去重」——两边分工：网关缓存「词库资源列表」，本组件缓存「编译自动机」。</p>
 *
 * <p><b>不持数据源、不调度</b>：词库变更的唯一入口是网关每请求经
 * {@code GatewayPluginContext.words} 下发——本组件无从自行取词（knot-plugin 不碰
 * 持久层是红线），故没有任何定时刷新：曾经的 {@code @Scheduled} 防御性重建只能用
 * 最近一次收到的词表重复编译同一份自动机，既拾取不到变更也无额外保障，
 * 2026-10-15 按 robin 判断整体移除。编译触发只认 {@link #snapshotFor(List)}。</p>
 *
 * <p>故障模式 fail-open：词表为空 / null 时返回空快照（守卫见空即放行），
 * 不冲刷已有编译产物。</p>
 */
@Component
public class ModerationWordCache {

    private static final List<ModerationTerm> EMPTY_WORDS = List.of();
    private static final ModerationSnapshot EMPTY_SNAPSHOT = ModerationSnapshot.of(EMPTY_WORDS);

    /**
     * 已编译快照与其内容版本。写序固定为「先快照后版本」，
     * 读者见版本命中即可安全读到对应快照（volatile happens-before）。
     */
    private volatile ModerationSnapshot snapshot = EMPTY_SNAPSHOT;
    private volatile long compiledVersion = 0L;

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

    private ModerationSnapshot compile(List<ModerationTerm> words, long version) {
        ModerationSnapshot fresh = ModerationSnapshot.of(words);
        this.snapshot = fresh;
        this.compiledVersion = version;
        return fresh;
    }
}
