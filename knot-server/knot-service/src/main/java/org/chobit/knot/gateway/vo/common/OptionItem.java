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
 *
 * <p>{@code baseUrl} 仅供应商账户选项使用（前端选中账户后回填模型 Base URL），非供应商账户恒为
 * null；属于非敏感的上游地址，可按 options 契约透出。</p>
 */
public record OptionItem(
        Object value,
        String label,
        String code,
        Boolean disabled,
        Map<String, Object> meta,
        String baseUrl
) {
    public OptionItem(Object value, String label) {
        this(value, label, null, null, null, null);
    }

    public OptionItem(Object value, String label, String code) {
        this(value, label, code, null, null, null);
    }
}
