package org.chobit.knot.gateway.adapter.support;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * payload（Map 形状）的通用读写原语：无供应商语义、无协议语义，各协议适配器与供应商 Support 共用。
 *
 * <p>归属判据：本类只放「怎么安全地读写一个 payload Map」的语法层原语；协议/供应商特有的
 * 形状转换不住本类（判例：{@code AwsBedrockConverseSupport} 的 {@code cast} / {@code nested} /
 * {@code firstPresent} / {@code stringOf} / {@code putLong} 曾作为私有辅助住在 AWS 供应商专用
 * 类里，2026-10-25 拆出——与 {@code move} 拆出 {@link RequestFieldSupport} 同一判据）。</p>
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
     * 按键序取第一个非 {@code null} 的值，全缺返回 {@code null}。
     *
     * <p>与 {@code MapNumberUtils.firstLong} 刻意不同：本方法认任意非 null（含 0 / false /
     * 空集合），数值版只认正数——别名吸收要回答的是「调用方给没给这个字段」，不是「给了多少」。
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
     *
     * <p>与 {@link #stringOf} 的分工：本方法带 trim 与「空白 = 未配置」语义，是凭据 / 配置类
     * map 的读取入口；{@code stringOf} 只做 null 安全的字符串化，不 trim。</p>
     *
     * <p>判例：authConfig 读取力学曾有两份逐字实现——{@code auth.AuthConfigSupport.value} 与
     * {@code support.aws.AwsBedrockEndpointResolver.read}（只差一个 blank-key guard），
     * 2026-10-25 合并至此、两个私有实现删除，调用方直调本方法；{@code AuthConfigSupport.value}
     * 曾短暂保留为委托壳，同批删除——<b>转发的壳没有知识增量，只是同一操作的两个名字</b>。</p>
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
