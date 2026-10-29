package org.chobit.knot.common.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 客户端回推地址（{@code callback_url} / {@code webhook_url}）字段名的<b>唯一判据</b>。
 *
 * <p>⭐⭐ <b>为什么住在 {@code common}（2026-10-29）</b>：它有两个互不依赖的消费方——</p>
 * <ul>
 *   <li>{@code knot-adapter} 的 {@code VideoRequestSupport}：把地址从发往上游的请求里<b>剥掉</b>，
 *       否则上游会把终态直接推给客户端，那笔用量既不经过轮询结算、也收不回；</li>
 *   <li>{@code knot-gateway} 的 {@code AsyncTaskSupport}：把地址<b>存下来</b>，供任务终态时回调。</li>
 * </ul>
 * <p>两边各写一份归一化比较，迟早一边加了一家新叫法、另一边没加——症状是
 * 「那家客户要么漏回调、要么地址漏给上游」，两种都没有任何运行期信号。收敛到一处后，
 * 新增一家用别的叫法（如 {@code notify_url}）只在这里加一项，剥除与提取同时生效。</p>
 *
 * <p>⚠ 归一化口径：忽略大小写与 {@code _} / {@code -}，于是 {@code callback_url} /
 * {@code callbackUrl} / {@code CALLBACK-URL} 同义。</p>
 */
public final class CallbackFieldSupport {

    /**
     * 回推地址的字段名（已归一化）。
     *
     * <p>覆盖 {@code callback_url} 与 {@code webhook_url} 两种业界叫法。
     * 缺一项的表现是「那家客户的回调地址照旧漏出去 / 收不到回调」，没有运行期信号。</p>
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
     * 从请求体里取出第一个非空回推地址；没有返回 {@code null}。
     *
     * <p>⭐ <b>必须递归</b>：Kling 把地址放在 {@code options.callback_url}，Luma / MiniMax 放在顶层——
     * 只扫顶层等于只认一半厂商。查找顺序是「本层直挂的回推字段优先，再逐层下钻」，
     * 于是顶层与嵌套同时出现时取顶层（更显式的那一个）。</p>
     *
     * <p>⚠ 只认 {@code String}：值不是字符串时视为没有配置，不把数字/对象强转成地址。</p>
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
