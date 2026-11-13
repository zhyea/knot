package org.chobit.knot.adapter.support;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.util.CallbackFieldSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频请求体的透传原语：剥掉客户端回推地址（{@code callback_url} / {@code webhook_url}）后原样转发。
 * 网关不替客户端校验请求形状、不代厂商补默认值，写错了由上游 400 原路返回。
 * 只做这一件与供应商无关的事，不带厂商字段语义；供应商特有的转换归各自的
 * {@code adapter.support.<供应商>.XxxSupport}。
 */
public final class VideoRequestSupport {

    private VideoRequestSupport() {
    }

    /**
     * 视频请求体的默认处理：请求体原样透传，仅剥掉客户端回推地址；
     * 没有可剥字段时返回同一个 Map 实例（不重建、不深拷贝）。
     *
     * @param context 上游请求上下文；{@code requestBody()} 为 {@code null} 时返回空 Map
     */
    public static Map<String, Object> passthroughBody(UpstreamRequestContext context) {
        Map<String, Object> source = context == null ? null : context.requestBody();
        return source == null ? new LinkedHashMap<>() : strip(source);
    }

    /**
     * 递归剥字段：顶层命中直接去掉，嵌套的 {@code Map} / {@code List} 里的命中同样去掉；
     * 返回新容器，不改动入参。
     */
    private static Map<String, Object> strip(Map<String, Object> source) {
        Map<String, Object> copy = null;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (isCallbackField(entry.getKey())) {
                if (copy == null) {
                    copy = new LinkedHashMap<>(source);
                }
                copy.remove(entry.getKey());
                continue;
            }
            Object stripped = stripValue(entry.getValue());
            if (stripped != entry.getValue()) {
                if (copy == null) {
                    copy = new LinkedHashMap<>(source);
                }
                copy.put(entry.getKey(), stripped);
            }
        }
        return copy == null ? source : copy;
    }

    /** 值里可能藏回推地址：{@code Map} / {@code List} 逐个下钻，其余原样返回（同一个实例 = 没改） */
    private static Object stripValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return strip(narrow(map));
        }
        if (value instanceof List<?> list) {
            return stripList(list);
        }
        return value;
    }

    private static List<Object> stripList(List<?> source) {
        List<Object> copy = null;
        for (int i = 0; i < source.size(); i++) {
            Object stripped = stripValue(source.get(i));
            if (stripped != source.get(i)) {
                if (copy == null) {
                    copy = new ArrayList<>(source);
                }
                copy.set(i, stripped);
            }
        }
        return copy == null ? castList(source) : copy;
    }

    /**
     * 字段名是否代表客户端回推地址；判据收敛在 {@link CallbackFieldSupport}，
     * 与 {@code AsyncTaskSupport} 的地址提取共用同一套字段名。
     */
    private static boolean isCallbackField(String key) {
        return CallbackFieldSupport.isCallbackField(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> narrow(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(List<?> list) {
        return (List<Object>) list;
    }
}
