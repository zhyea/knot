package org.chobit.knot.gateway.model.usage;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.usage.ModelUsageNormalizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关向调用方输出的 {@code model_usage} 负载：同一份用量同时给出三种视图。
 *
 * <p>仅在路由消费者的 {@code return_usage_detail} 打开时注入响应，三种视图的用途分别是：
 * <ul>
 *   <li>{@code usage}：归一化后的统一用量，跨厂商字段一致，业务侧应以此为准；</li>
 *   <li>{@code raw}：上游原始 {@code usage} 对象原文，逐字透传，用于排障与对账；</li>
 *   <li>{@code billing}：本网关按计费规则算出的金额与明细。</li>
 * </ul>
 *
 * <p>三者缺失时为 {@code null} 并在 JSON 中省略：例如上游未上报用量时
 * {@code raw} 不出现，而 {@code usage} 仍可能由响应体推断出多模态张数。</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelUsagePayload(ModelUsage usage,
                                Map<String, Object> raw,
                                NormalizedUsage billing) {

    /**
     * 由账目组装输出负载。
     *
     * <p>{@code usage} 走两级兜底：优先对上游原始 usage 做归一化；
     * 上游未上报 usage 时退回计费输入视图推断（多模态场景靠此保留张数 / 秒数）。</p>
     */
    public static ModelUsagePayload of(UsageAccounting accounting) {
        if (accounting == null || accounting.isEmpty()) {
            return null;
        }
        Map<String, Object> raw = accounting.rawUsage();
        ModelUsage usage = ModelUsageNormalizer.fromSource(
                raw,
                accounting.rawBody(),
                accounting.billing()
        );
        return new ModelUsagePayload(usage, copy(raw), accounting.normalized());
    }

    private static Map<String, Object> copy(Map<String, Object> raw) {
        return raw == null || raw.isEmpty() ? null : new LinkedHashMap<>(raw);
    }
}
