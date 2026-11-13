package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：模型池成员（权重与优先级）。
 * 字段逐字平移 {@code ModelPoolItemEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record ModelPoolItemConfigVo(
        Long id,
        String poolCode,
        String modelCode,
        Integer weight,
        Integer priority,
        Integer status,
        String modelName,
        String modelType,
        String providerAccountCode,
        String providerName
) {
}
