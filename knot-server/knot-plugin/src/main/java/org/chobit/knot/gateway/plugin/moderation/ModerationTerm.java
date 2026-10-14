package org.chobit.knot.gateway.plugin.moderation;

/**
 * 敏感词条的中性表示：跨模块边界时用它，避免 knot-plugin 依赖 knot-dal 的持久化实体。
 *
 * <p>{@code severity} / {@code updatedAtMillis} 可空：分别由引擎兜底默认严重度、快照兜底版本 0。</p>
 */
public record ModerationTerm(String word,
                             String normalizedWord,
                             String categoryCode,
                             Integer severity,
                             /** 词条更新时间（epoch 毫秒）：构成词库版本，供审计事件回溯 */
                             Long updatedAtMillis) {

    /**
     * Builds a term from the persisted shape. Executes the public operation.
     */
    public static ModerationTerm of(String word,
                                    String normalizedWord,
                                    String categoryCode,
                                    Integer severity,
                                    Long updatedAtMillis) {
        return new ModerationTerm(word, normalizedWord, categoryCode, severity, updatedAtMillis);
    }
}
