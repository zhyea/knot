package org.chobit.knot.gateway.vo.provider;

import java.util.List;

/**
 * 供应商账户鉴权策略选项，由 {@code UpstreamAuthApplierCatalog} 单一来源下发
 * （每个 {@code UpstreamAuthApplier} 实现即一个选项）。
 *
 * <p>{@code credentialTypes} 是该策略适用的认证类型 code 列表（ProviderCredentialTypeEnum），
 * 前端据此按已选认证类型筛选可选项。</p>
 */
public record AuthApplierItem(String code,
                              String label,
                              List<String> credentialTypes) {
}
