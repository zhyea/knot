package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：应用（按 app_code 鉴权、取配额策略）。
 * 字段逐字平移 {@code AppEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record AppConfigVo(
        Long id,
        String appCode,
        String name,
        String deptCode,
        String deptName,
        String ownerUsername,
        String ownerRealName,
        String remark,
        Integer isDeleted,
        Integer status,
        Long boundCount
) {
}
