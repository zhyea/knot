package org.chobit.knot.gateway.constants.enums;

/**
 * {@code GET /api/common/enums} 的单个选项。
 *
 * <p>响应结构从 {@code code -> label} 的 map 改为数组，是因为 JSON object key 必然是字符串：
 * {@code {"1":"启用"}} 表达的数字在反序列化后与 {@code {"ENABLED":"启用"}} 无法区分类型，
 * 数字 code 的语义在传输层丢失。数组结构的 {@code code} 保留原始 JSON 类型
 * （int 出 number，String 出 string）。</p>
 *
 * <p>{@code code} 声明为 {@code Object} 是因为同一份响应里既有数值状态枚举也有字符串业务枚举；
 * Jackson 按其运行时类型序列化，前端按 {@code typeof} 消费。类型正确性由
 * {@code EnumOptionRegistryTest} 的 JSON token 断言守护。</p>
 */
public record EnumOptionItem(Object code, String label) {

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static EnumOptionItem of(EnumOption option) {
        return new EnumOptionItem(option.code(), option.label());
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static EnumOptionItem of(NumericEnumOption option) {
        return new EnumOptionItem(option.code(), option.label());
    }
}
