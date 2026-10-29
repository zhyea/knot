package org.chobit.knot.adapter.support;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.util.CallbackFieldSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频请求体的透传原语：<b>剥掉客户端回推地址后原样转发</b>。
 *
 * <p>视频生成适配器的职责到此为止（规范见 {@code 60-business-rules.md}「视频适配器只做透传」）：
 * 请求形状由客户端自己负责，写错了让上游 400 原路返回，网关不替客户端校验、不代厂商补默认值。</p>
 *
 * <p>⭐ <b>必须剥 callback 的理由不是洁癖，是计费完整性</b>：一旦客户端的
 * {@code callback_url} / {@code webhook_url} 漏到供应商，供应商会把终态直接推给客户端——
 * 客户端因此拿到了产物却没经过 knot 的轮询与终态结算，那笔用量既查不到也收不回。
 * 同时它还是一个可被客户端指定目标的出站地址，安全面也不该由客户端决定。</p>
 *
 * <p>⚠ 边界：本类只做<b>一件</b>与供应商无关的事，不带任何厂商字段语义。
 * 供应商特有的转换（形状改写、必填兜底）属于「特殊情况」，归各自的
 * {@code adapter.support.<供应商>.XxxSupport}，不要往这里加。</p>
 */
public final class VideoRequestSupport {

    private VideoRequestSupport() {
    }

    /**
     * 视频请求体的默认处理：请求体原样透传，仅剥掉客户端回推地址。
     *
     * <p>没有可剥字段时<b>返回同一个 Map 实例</b>（不重建、不深拷贝）：视频以外的请求走
     * {@code PassthroughUpstreamAdapter} 时也调用本方法，每个请求都复制一份请求体没有意义。</p>
     *
     * @param context 上游请求上下文；{@code requestBody()} 为 {@code null} 时返回空 Map
     */
    public static Map<String, Object> passthroughBody(UpstreamRequestContext context) {
        Map<String, Object> source = context == null ? null : context.requestBody();
        return source == null ? new LinkedHashMap<>() : strip(source);
    }

    /**
     * 递归剥字段：顶层命中直接去掉；嵌套的 {@code Map} / {@code List} 里的命中同样去掉。
     *
     * <p>为什么必须递归：Kling 把回推地址放在 {@code options.callback_url}，
     * Luma / MiniMax 放在顶层——只扫一层等于只挡住一半厂商。</p>
     *
     * <p>返回新容器而不是原地改：{@code UpstreamRequestContext.requestBody()} 是同一份快照，
     * 异步任务要拿它落 {@code request_json} 供终态计费复现，改坏了账就复现不出来。</p>
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
     * 字段名是否代表客户端回推地址。
     *
     * <p>⭐ 判据收敛到 {@link CallbackFieldSupport}：同一套字段名还要给
     * {@code AsyncTaskSupport} 用来<b>提取</b>地址（而非剥除）。两处各写一份，
     * 迟早一家客户在这条链上被剥掉、在那条链上又取不到。</p>
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
