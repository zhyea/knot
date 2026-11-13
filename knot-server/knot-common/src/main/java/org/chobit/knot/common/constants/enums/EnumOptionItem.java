package org.chobit.knot.common.constants.enums;

/**
 * {@code GET /api/enum-options} 的单个选项。
 * 数组结构的 {@code code} 保留原始 JSON 类型（int 出 number，String 出 string）；
 * 声明为 {@code Object} 以容纳同一份响应里的数值状态枚举与字符串业务枚举，前端按 {@code typeof} 消费。
 */
public record EnumOptionItem(Object code, String label) {

    public static EnumOptionItem of(EnumOption option) {
        return new EnumOptionItem(option.code(), option.label());
    }

    public static EnumOptionItem of(NumericEnumOption option) {
        return new EnumOptionItem(option.code(), option.label());
    }
}
