package org.chobit.knot.svc.vo.model;

import java.time.LocalDateTime;

/**
 * 外部模型条目（从供应商目录同步下来的模型）载体。
 *
 * <p>⚠ <b>{@code rawJson} 不能裁</b>：详情抽屉的「JSON」页签直接渲染它，
 * 用户靠它排查「供应商到底回了什么」。同族的 {@code syncHash} 是内部指纹、
 * 前端不用，但它留在 record 里是因为同步链路的两端都要读——真要裁得先改 enrich 逻辑。</p>
 *
 * <p>{@code ignored} 是 <b>Boolean 而非 int</b>：它表达「用户主动忽略这条」，三态（null 视为未忽略）
 * 在同步覆盖时有意义，别改成 0/1。</p>
 */
public record ExternalModelItem(
        Long id,
        String sourceCode,
        String modelId,
        String canonicalSlug,
        String modelName,
        String providerName,
        String modelUrl,
        LocalDateTime modelCreatedAt,
        String description,
        Integer contextLength,
        String inputModalitiesJson,
        String outputModalitiesJson,
        String rawJson,
        String normalizedName,
        String modelType,
        String tagsJson,
        Integer maxCompletionTokens,
        Long logicalModelId,
        Boolean ignored,
        /** 同步状态（ExternalModelSyncStatusEnum）：1-待同步 2-已同步 3-失败 */
        Integer syncStatus,
        String syncHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
