package org.chobit.knot.svc.vo.common.meta;

/**
 * 角色下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('roleCode', r.code)} 对齐）。
 * {@code value} 是 {@code ks_roles.id}，角色编码 {@code roleCode} 是绑定键之外的业务属性，落在 meta 而非顶层。
 */
public record RoleOptionMeta(String roleCode) {
}
