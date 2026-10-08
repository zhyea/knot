package org.chobit.knot.gateway.vo.common.meta;

/**
 * 角色下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('roleCode', r.code)} 对齐）。
 *
 * <p>角色 options 的 {@code value} 是 {@code ks_roles.id}，角色编码 {@code roleCode} 是绑定键之外的业务属性，
 * 因此落在 meta 而非顶层（顶层 {@code code} 已按 options-refactor-constraints.md 移除）。</p>
 */
public record RoleOptionMeta(String roleCode) {
}
