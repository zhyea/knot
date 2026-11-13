package org.chobit.knot.svc.vo.common.meta;

/**
 * 部门下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('deptCode', d.dept_code)} 对齐）。
 * {@code value} 是 {@code ks_departments.id}，部门编码 {@code deptCode} 是绑定键之外的业务属性，落在 meta 而非顶层。
 */
public record DepartmentOptionMeta(String deptCode) {
}
