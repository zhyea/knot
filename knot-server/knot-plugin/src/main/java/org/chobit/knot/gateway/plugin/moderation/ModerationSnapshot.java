package org.chobit.knot.gateway.plugin.moderation;

import java.util.List;

/**
 * 词库快照：编译好的自动机 + 版本（用于审计事件回溯）。
 */
public record ModerationSnapshot(AhoCorasickMatcher matcher, long version) {

    /**
     * 按词条列表编译快照；空词库返回空自动机、版本 0。
     */
    public static ModerationSnapshot of(List<ModerationTerm> words) {
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(words);
        long version = 0L;
        if (words != null) {
            version = words.stream()
                    .filter(word -> word.updatedAtMillis() != null)
                    .mapToLong(ModerationTerm::updatedAtMillis)
                    .max()
                    .orElse(0L);
        }
        return new ModerationSnapshot(matcher, version);
    }

    /**
     * 词库为空或自动机缺失时无需扫描。
     */
    public boolean empty() {
        return matcher == null || matcher.empty();
    }
}
