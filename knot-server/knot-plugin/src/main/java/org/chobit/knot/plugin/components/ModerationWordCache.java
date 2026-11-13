package org.chobit.knot.plugin.components;

import org.chobit.knot.common.moderation.ModerationTerm;
import org.chobit.knot.plugin.moderation.ModerationSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 词库编译缓存：把网关每请求经插件上下文下发的词表编译为 Aho-Corasick 自动机，
 * 按内容版本（{@link ModerationTerm#versionOf}）去重，守卫经 IOC 取用。
 *
 * 不持数据源、不调度，编译触发只认 {@link #snapshotFor(List)}；故障模式 fail-open——
 * 词表为空 / {@code null} 时返回空快照（守卫见空即放行），不冲刷已有编译产物。
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
