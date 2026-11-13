package org.chobit.knot.svc.vo.common;

/**
 * 下拉候选项。{@code value}/{@code label} 为组件唯一必需字段；{@code meta} 承载下拉需要的非敏感派生信息，
 * 严禁返回 credential、secretKey、完整 {@code configJson}、审计字段、大段描述；全部键由 SQL 侧 {@code json_object} 生成。
 * 顶层没有 {@code code}：绑定键恒为 {@code value}，业务属性恒读资源专属 {@code meta}，禁止按资源新增顶层字段。
 * 泛型 {@code M} 为 {@code meta} 的强类型（各资源 DTO 在 {@code vo.common.meta} 包，由 {@code OptionConverter} 反序列化）；
 * {@code value} 为 {@link Object}（id 型=Long、code 型=String，由 {@code OptionRow<V>} 守护）。
 */
public record OptionItem<M>(
        Object value,
        String label,
        Boolean disabled,
        M meta
) {
    public OptionItem(Object value, String label) {
        this(value, label, null, null);
    }
}
