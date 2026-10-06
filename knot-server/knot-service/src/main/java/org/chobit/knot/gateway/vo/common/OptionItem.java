package org.chobit.knot.gateway.vo.common;

import java.util.Map;

/**
 * 下拉候选项。
 *
 * <p>{@code value}/{@code label} 为组件唯一必需字段；{@code code}/{@code meta} 只承载下拉确实需要的
 * 非敏感业务信息。严禁返回 credential、secretKey、完整 configJson、审计字段、大段描述
 * （见 options-refactor-constraints.md 第 0 节）。</p>
 *
 * <p>{@code value} 类型随资源：id 型资源为数值、code 型资源为字符串，前端不得自行猜测或强制转换。</p>
 */
public record OptionItem(
        Object value,
        String label,
        String code,
        Boolean disabled,
        Map<String, Object> meta
) {
    public OptionItem(Object value, String label) {
        this(value, label, null, null, null);
    }

    public OptionItem(Object value, String label, String code) {
        this(value, label, code, null, null);
    }
}
