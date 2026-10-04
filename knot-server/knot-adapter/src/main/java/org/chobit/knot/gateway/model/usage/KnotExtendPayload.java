package org.chobit.knot.gateway.model.usage;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.usage.ModelUsageNormalizer;

/**
 * 网关向调用方输出的 {@code knot_extend} 负载：同一份用量给出两种视图。
 *
 * <p>仅在路由消费者的 {@code return_usage_detail} 打开时注入响应，两视图的用途分别是：
 * <ul>
 *   <li>{@code usage}：归一化后的统一用量，跨厂商字段一致，业务侧应以此为准；</li>
 *   <li>{@code billing}：本网关按计费规则算出的总价与计费明细。</li>
 * </ul>
 *
 * <p>上游原始 {@code usage} 对象不再对外透传：它已完整参与 {@code usage} 归一化，
 * 排障所需字段在 {@code usage} 中均有对应项，重复输出只会让调用方在两个口径间选错。
 *
 * <p>{@code billing} 在无计费规则可命中时为 {@code null} 并在 JSON 中省略；
 * {@code usage} 在上游完全未上报用量且无法从响应体推断时同样省略。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnotExtendPayload(ModelUsage usage,
                                NormalizedUsage billing) {

    /**
     * 由账目组装输出负载。
     *
     * <p>{@code usage} 走两级兜底：优先对上游原始 usage 做归一化；
     * 上游未上报 usage 时退回计费输入视图推断（多模态场景靠此保留张数 / 秒数）。</p>
     */
    public static KnotExtendPayload of(UsageAccounting accounting) {
        if (accounting == null || accounting.isEmpty()) {
            return null;
        }
        ModelUsage usage = ModelUsageNormalizer.fromSource(
                accounting.rawUsage(),
                accounting.rawBody(),
                accounting.billing()
        );
        NormalizedUsage billing = accounting.normalized();
        if (usage == null && billing == null) {
            return null;
        }
        return new KnotExtendPayload(usage, billing);
    }
}
