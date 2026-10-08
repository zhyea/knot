package org.chobit.knot.gateway.vo.common.meta;

/**
 * 供应商账户下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('baseUrl', pa.base_url)} 对齐）。
 *
 * <p>仅承载下拉确实需要、且非敏感的派生信息；{@code baseUrl} 是上游网关地址（非凭据），
 * 不得在此扩出 secretKey / encryptedConfig 等敏感字段（见 options-refactor-constraints.md 第 0 节）。</p>
 */
public record ProviderAccountMeta(String baseUrl) {
}
