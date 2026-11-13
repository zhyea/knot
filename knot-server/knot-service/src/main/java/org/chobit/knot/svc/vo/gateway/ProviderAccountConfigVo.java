package org.chobit.knot.svc.vo.gateway;

import java.time.LocalDateTime;

/**
 * 网关配置快照：供应商账户（baseUrl 与账户编码）。
 * 字段逐字平移 {@code ProviderAccountEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record ProviderAccountConfigVo(
        Long id,
        String providerCode,
        String providerName,
        String code,
        String baseUrl,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
