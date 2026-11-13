package org.chobit.knot.svc.vo.gateway;

import java.time.LocalDateTime;

/**
 * 网关配置快照：供应商模型（上游 model 参数、计费规则绑定、模型族回退）。
 * 字段逐字平移 {@code ModelEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record ModelConfigVo(
        Long id,
        String providerAccountCode,
        String providerName,
        String providerCode,
        String modelCode,
        String upstreamModel,
        String logicalModelCode,
        String modelType,
        String modelFamilyCode,
        String version,
        String basePath,
        String remark,
        String billingRuleCode,
        Integer status,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
