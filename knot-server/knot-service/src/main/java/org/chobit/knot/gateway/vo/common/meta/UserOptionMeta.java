package org.chobit.knot.gateway.vo.common.meta;

/**
 * 用户下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('username', u.username)} 对齐）。
 *
 * <p>用户 options 的 {@code value} 是 {@code ks_users.id}，登录名 {@code username} 是绑定键之外的业务属性，
 * 因此落在 meta 而非顶层（顶层 {@code code} 已按 options-refactor-constraints.md 移除）。</p>
 *
 * <p>仅承载下拉确实需要、且非敏感的字段；不得扩出密码、邮箱、手机号等（见约束第 0 节）。</p>
 */
public record UserOptionMeta(String username) {
}
