package org.chobit.knot.svc.vo.common.meta;

/**
 * 应用下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('appCode', a.app_code)} 对齐）。
 * {@code value} 是 {@code kb_apps.id}，业务码 {@code appCode} 是绑定键之外的业务属性，落在 meta 而非顶层。
 * 应用凭据（appSecret）属敏感字段，禁止进入 meta。
 */
public record AppOptionMeta(String appCode) {
}
