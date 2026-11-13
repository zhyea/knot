package org.chobit.knot.adapter.support;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * payload（Map 形状）的通用读写原语：无供应商语义、无协议语义，各协议适配器与供应商 Support 共用。
 * 只放「怎么安全地读写一个 payload Map」的语法层原语，协议/供应商特有的形状转换不住本类。
 */
public final class PayloadMapSupport {

    private PayloadMapSupport() {
    }

    /** unchecked cast：{@code Map<?, ?>} → {@code Map<String, Object>}，调用方已做过 instanceof 判定。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> cast(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    /**
     * 两层下钻：任一层不是对象（或缺失）返回 {@code null}。
     */
    public static Map<String, Object> nested(Map<String, Object> body, String outer, String inner) {
        Object node = body.get(outer);
        if (!(node instanceof Map<?, ?> raw)) {
            return null;
        }
        Object child = cast(raw).get(inner);
        return child instanceof Map<?, ?> map ? cast(map) : null;
    }

    /**
     * 按键序取第一个非 {@code null} 的值，全缺返回 {@code null}；认任意非 {@code null}
     * （含 {@code 0} / {@code false} / 空集合）。
     */
    public static Object firstPresent(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** null 安全的字符串化：{@code null} → 空串，其余 {@code String.valueOf}。 */
    public static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 按键读取字符串值：{@code null} map / 空白键 → {@code null}；取值 trim，空白 → {@code null}。
     * 带 trim 与「空白 = 未配置」语义，是凭据 / 配置类 map 的读取入口；{@link #stringOf} 不 trim。
     */
    public static String text(Map<String, Object> source, String key) {
        if (source == null || StringUtils.isBlank(key)) {
            return null;
        }
        return StringUtils.trimToNull(stringOf(source.get(key)));
    }

    /** 写入 long 并把负值钳为 0（usage 字段不接受负数）。 */
    public static void putLong(Map<String, Object> target, String key, long value) {
        target.put(key, Math.max(0L, value));
    }
}
