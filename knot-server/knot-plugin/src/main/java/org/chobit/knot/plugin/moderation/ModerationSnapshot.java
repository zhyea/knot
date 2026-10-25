package org.chobit.knot.plugin.moderation;

import org.chobit.knot.gateway.moderation.ModerationTerm;

import java.util.List;

/**
 * 词库快照：编译好的自动机 + 版本（用于审计事件回溯）。
 *
 * <p>由 {@link ModerationGuard} 从上下文下发的全量词条编译而来，是 knot-plugin 的内部实现——
 * 契约层（{@code GatewayPluginContext}）只过中性词表，不过编译产物。</p>
 */
public record ModerationSnapshot(AhoCorasickMatcher matcher, long version) {

    /**
     * 按词条列表编译快照；空词库返回空自动机、版本 0。
     */
    public static ModerationSnapshot of(List<ModerationTerm> words) {
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(words);
        return new ModerationSnapshot(matcher, ModerationTerm.versionOf(words));
    }

    /**
     * 词库为空或自动机缺失时无需扫描。
     */
    public boolean empty() {
        return matcher == null || matcher.empty();
    }
}
