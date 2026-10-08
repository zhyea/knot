package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;
import java.util.List;

/**
 * 数值型枚举选项：与 {@link EnumOption}（字符串 code）并列，供业务状态类枚举实现。
 *
 * <p>存在两套接口而不是把 {@code EnumOption} 泛型化的原因：{@code EnumOption} 已有 18 个实现类，
 * 泛型化会打断全部实现，且 {@code EnumOption<C>[]} 无法在 Java 中安全构造（泛型数组禁令）。
 * 新增接口后既有枚举零改动。</p>
 *
 * <p>code 从 1 开始递增；二态枚举用 {@code 1 = 正向 / 0 = 关闭}。{@code 0} 是合法值，
 * 所有判断必须显式比较，禁止依赖 truthy。</p>
 */
public interface NumericEnumOption {

    /**
     * Returns the numeric option code persisted in business tables.
     */
    int code();

    /**
     * Returns the display label shown by the front end.
     */
    String label();

    /**
     * 按 code 查找枚举，未命中返回 {@code null}（不抛异常，供兼容解析与可选字段使用）。
     */
    static <E extends Enum<E> & NumericEnumOption> E fromCode(E[] values, Integer code) {
        if (code == null) {
            return null;
        }
        for (E item : values) {
            if (item.code() == code) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按 code 查找枚举，未命中抛校验错误（Service / Controller 边界一律用这个）。
     */
    static <E extends Enum<E> & NumericEnumOption> E requireCode(E[] values, Integer code, String errorMessage) {
        E item = fromCode(values, code);
        if (item == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return item;
    }

    /**
     * 全部 code，供查询下拉与批量校验使用；顺序与枚举声明顺序一致。
     */
    static <E extends Enum<E> & NumericEnumOption> List<Integer> codes(E[] values) {
        return Arrays.stream(values).map(NumericEnumOption::code).toList();
    }

    /**
     * 校验 code 合法并返回规范化后的值。
     */
    static <E extends Enum<E> & NumericEnumOption> int requireCodeValue(E[] values, Integer code, String errorMessage) {
        return requireCode(values, code, errorMessage).code();
    }
}
