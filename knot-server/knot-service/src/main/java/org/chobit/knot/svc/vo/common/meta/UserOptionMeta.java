package org.chobit.knot.svc.vo.common.meta;

/**
 * 用户下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('username', u.username)} 对齐）。
 * {@code value} 是 {@code ks_users.id}，登录名 {@code username} 是绑定键之外的业务属性，落在 meta 而非顶层。
 * 仅承载下拉需要的非敏感字段，不得扩出密码、邮箱、手机号等。
 */
public record UserOptionMeta(String username) {
}
