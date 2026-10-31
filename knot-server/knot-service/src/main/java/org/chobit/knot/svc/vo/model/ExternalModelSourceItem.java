package org.chobit.knot.svc.vo.model;

import java.time.LocalDateTime;

/**
 * 外部模型源（OpenRouter / 硅基流动 等目录来源）载体。
 *
 * <p>{@code syncStatus} 是源级启用状态（EnabledStatusEnum：1-启用 0-停用），
 * 与 {@link ExternalModelItem#syncStatus()}（条目级同步状态，另一套枚举）不是同一件事——
 * 两个同名字段分属两张表的两套枚举，看代码时别串。</p>
 */
public record ExternalModelSourceItem(
        Long id,
        String sourceCode,
        String sourceName,
        String sourceUrl,
        String apiUrl,
        String sourceType,
        /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
        Integer status,
        LocalDateTime lastSyncAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
