package org.chobit.knot.common.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 客户端回推地址（{@code callback_url} / {@code webhook_url}）字段名的唯一判据。
 * 归一化口径：忽略大小写与 {@code _} / {@code -}，故 {@code callback_url} / {@code callbackUrl} /
 * {@code CALLBACK-URL} 同义。
 */
public final class CallbackFieldSupport {

    /**
     * 回推地址的字段名（已归一化），覆盖 {@code callback_url} 与 {@code webhook_url} 两种叫法。
     */
    private static final Set<String> CALLBACK_FIELDS = Set.of("callbackurl", "webhookurl");

    private CallbackFieldSupport() {
    }

    /** 字段名是否代表一个客户端回推地址（归一化后比较，见类注释） */
    public static boolean isCallbackField(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return CALLBACK_FIELDS.contains(normalized);
    }

    /**
     * 从请求体里取出第一个非空回推地址；没有返回 {@code null}。递归查找，本层直挂的回推字段优先，
     * 再逐层下钻，顶层与嵌套同时出现时取顶层。只认 {@code String}，其他类型视为未配置。
     *
     * @param body 客户端原始请求体（可为 {@code null}）
     */
    public static String callbackUrlOf(Map<String, Object> body) {
        return body == null ? null : search(body);
    }

    private static String search(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && isCallbackField(key)) {
                    String url = text(entry.getValue());
                    if (url != null) {
                        return url;
                    }
                }
            }
            for (Object nested : map.values()) {
                String found = search(nested);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (value instanceof Iterable<?> list) {
            for (Object item : list) {
                String found = search(item);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (!(value instanceof String raw)) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
