package org.chobit.knot.gateway.moderation;

import java.util.List;
import java.util.Objects;

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

    /**
     * 词库版本：全部词条 {@code updatedAtMillis} 的最大值，空库或无时间值为 0。
     *
     * <p>缓存失效比对（网关侧）与快照版本（插件侧）同用此算法，避免两处口径漂移——
     * 与 service 的 SQL {@code MAX(updated_at)}（同一批启用未删行）天然一致。</p>
     */
    public static long versionOf(List<ModerationTerm> terms) {
        if (terms == null || terms.isEmpty()) {
            return 0L;
        }
        return terms.stream()
                .map(ModerationTerm::updatedAtMillis)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
    }
}
