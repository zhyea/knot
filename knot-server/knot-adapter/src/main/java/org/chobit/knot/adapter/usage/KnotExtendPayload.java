package org.chobit.knot.adapter.usage;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.UsageAccounting;

/**
 * 网关向调用方输出的 {@code knot_extend} 负载：同一份用量给出两种视图，仅在路由消费者的 {@code return_usage_detail} 打开时注入响应。
 * {@code usage} 是归一化后的统一用量（业务侧以此为准），{@code billing} 是网关按计费规则算出的总价与计费明细。
 * 上游原始 usage 原文以并列的 {@code usage} 字段随 SSE 事件输出（同步整包响应不注入；异步任务查询响应另从 {@code terminal_body} 取原文）。
 * {@code billing} 无计费规则命中时为 {@code null} 并在 JSON 中省略；{@code usage} 上游未上报且无法从响应体推断时同样省略。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnotExtendPayload(ModelUsage usage,
                                NormalizedUsage billing) {

    /**
     * 由账目组装输出负载：{@code usage} 直接取提取器产出的统一用量视图（与 {@code billing} 同口径）。
     */
    public static KnotExtendPayload of(UsageAccounting accounting) {
        if (accounting == null || accounting.isEmpty()) {
            return null;
        }
        ModelUsage usage = accounting.usage();
        NormalizedUsage billing = accounting.normalized();
        if (usage == null && billing == null) {
            return null;
        }
        return new KnotExtendPayload(usage, billing);
    }
}
