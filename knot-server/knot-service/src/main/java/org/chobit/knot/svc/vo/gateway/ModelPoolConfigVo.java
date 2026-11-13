package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：模型池（池内选择与粘性路由策略）。
 * 字段逐字平移 {@code ModelPoolEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record ModelPoolConfigVo(
        Long id,
        String poolCode,
        String name,
        String logicalModelCode,
        String logicalModelName,
        String modelType,
        String selectionStrategy,
        String stickyPolicy,
        Integer status,
        Integer isDeleted,
        String remark
) {
}
