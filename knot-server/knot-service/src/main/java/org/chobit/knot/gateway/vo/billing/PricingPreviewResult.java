package org.chobit.knot.gateway.vo.billing;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 计费方案试算结果：**方案层怎么看这一刻**。
 *
 * <p>{@code phase}/{@code reason}/{@code multiplier} 直接来自 {@code PeakOffPeakResolver}，
 * 与管理端配置页（F1 的时间判定测试）走同一套判定，禁止另写一套。
 * {@code prices} 是「已乘过倍率」的最终单价：TOKEN 模式给 input/output/cacheRead/cacheWrite 四项，
 * 其余模式给 {@code default} 一项。
 */
public record PricingPreviewResult(Long ruleId,
                                   String ruleCode,
                                   String billingMode,
                                   String pricingPlan,
                                   String currency,
                                   String unit,
                                   int unitSize,
                                   /** 实际用于判定的发生时间（ISO-8601 UTC） */
                                   String occurredAt,
                                   /** 配置里的时区；高低峰方案才有值 */
                                   String timezone,
                                   /** 命中相位：PEAK / OFF_PEAK；非高低峰方案为 null */
                                   String phase,
                                   /** 命中原因：PEAK_WINDOW / HOLIDAY / MAKE_UP_WORKDAY / DEFAULT / NO_TIMESTAMP */
                                   String reason,
                                   /** 相位倍率 */
                                   BigDecimal multiplier,
                                   /** 最终单价：价格种类 -> 单价 */
                                   Map<String, BigDecimal> prices) {
}
