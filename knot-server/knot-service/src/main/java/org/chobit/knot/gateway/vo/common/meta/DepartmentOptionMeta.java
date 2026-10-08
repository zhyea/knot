package org.chobit.knot.gateway.vo.common.meta;

/**
 * 部门下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('deptCode', d.dept_code)} 对齐）。
 *
 * <p>部门 options 的 {@code value} 是 {@code ks_departments.id}，部门编码 {@code deptCode} 是绑定键之外的业务属性，
 * 因此落在 meta 而非顶层（顶层 {@code code} 已按 options-refactor-constraints.md 移除）。</p>
 */
public record DepartmentOptionMeta(String deptCode) {
}
