package org.chobit.knot.svc.vo.model;

import java.time.LocalDateTime;

/**
 * 外部模型条目（从供应商目录同步下来的模型）载体。
 * {@code rawJson} 供详情抽屉的「JSON」页签直接渲染，不能裁；{@code syncHash} 是内部指纹，前端不用，同步链路两端都要读。
 * {@code ignored} 是 Boolean 而非 int，表达「用户主动忽略这条」，三态（null 视为未忽略），别改成 0/1。
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
