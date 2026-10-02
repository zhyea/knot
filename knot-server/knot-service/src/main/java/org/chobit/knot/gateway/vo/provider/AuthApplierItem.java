package org.chobit.knot.gateway.vo.provider;

/**
 * 供应商账户鉴权策略选项，由 {@code UpstreamAuthApplierCatalog} 单一来源下发
 * （每个 {@code UpstreamAuthApplier} 实现即一个选项）。
 */
public record AuthApplierItem(String code,
                              String label) {
}
