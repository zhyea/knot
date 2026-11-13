package org.chobit.knot.svc.vo.common.meta;

/**
 * 供应商账户下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 json_object 对齐），仅承载下拉需要的非敏感派生信息。
 * {@code baseUrl} 是上游网关地址（非凭据），不得扩出 {@code secretKey} / {@code encryptedConfig} 等敏感字段。
 * {@code providerCode} / {@code providerName} 供前端按供应商分组渲染两级树（父行=供应商，子行=账户；选中值恒为账户 code）。
 */
public record ProviderAccountMeta(String providerCode, String providerName, String baseUrl) {
}
