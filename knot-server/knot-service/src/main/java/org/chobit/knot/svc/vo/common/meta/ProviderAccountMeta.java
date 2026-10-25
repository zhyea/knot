package org.chobit.knot.svc.vo.common.meta;

/**
 * 供应商账户下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 json_object 对齐）。
 *
 * <p>仅承载下拉确实需要、且非敏感的派生信息；{@code baseUrl} 是上游网关地址（非凭据），
 * 不得在此扩出 secretKey / encryptedConfig 等敏感字段（见 options-refactor-constraints.md 第 0 节）。</p>
 *
 * <p>{@code providerCode} / {@code providerName} 供前端把账户列表按供应商分组渲染成两级树
 * （父行=供应商只展开/折叠，子行=账户；选中值恒为账户 code，供应商不参与绑定）。</p>
 */
public record ProviderAccountMeta(String providerCode, String providerName, String baseUrl) {
}
