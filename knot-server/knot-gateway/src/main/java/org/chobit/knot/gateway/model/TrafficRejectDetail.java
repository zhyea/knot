package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.chobit.knot.gateway.traffic.QuotaDimension;
import org.chobit.knot.gateway.traffic.TrafficDecision;
import org.chobit.knot.gateway.traffic.TrafficRejectReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 限流 / 限额拒绝的结构化明细：告诉调用方「谁被限了、限的是哪个维度、上限与已用多少、什么时候恢复」。
 *
 * <p>随 429 响应体的 {@code error.detail} 返回。数值一律换算成<b>自然单位</b>再输出——
 * 成本维度内部按纳元（1e-8）计数，直接透给调用方是无法读的 {@code 1000000000}，
 * 这里统一还原成金额（配合 {@link #unit} 给出币种）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrafficRejectDetail(String resourceType,
                                  Long resourceId,
                                  String reason,
                                  String dimension,
                                  String unit,
                                  BigDecimal limit,
                                  BigDecimal used,
                                  String resetAt,
                                  long resetAfterSeconds) {

    private static final DateTimeFormatter RESET_AT = DateTimeFormatter.ISO_INSTANT;

    /**
     * 按判定结果与当前时刻生成明细。
     *
     * @param decision 被拒的流量判定（必须 {@code allowed=false}）
     * @param nowMillis 当前时刻，用于算 {@code resetAfterSeconds}
     */
    public static TrafficRejectDetail of(TrafficDecision decision, long nowMillis) {
        TrafficRejectReason reason = decision.reason();
        boolean cost = reason == TrafficRejectReason.QUOTA_COST;
        return new TrafficRejectDetail(
                decision.resourceType(),
                decision.resourceId(),
                reason.name(),
                reason.dimension(),
                cost ? decision.currency() : reason.unit(),
                amount(decision.limit(), cost),
                amount(decision.used(), cost),
                resetAt(decision.resetAtMillis()),
                resetAfterSeconds(decision.resetAtMillis(), nowMillis)
        );
    }

    /**
     * 面向调用方的一句话说明，同时作为响应体的 {@code error.message}。
     */
    public String describe() {
        String subject = resourceType + "#" + resourceId;
        String headline = reason != null && reason.startsWith("QUOTA")
                ? "Quota exceeded for " + subject
                : "Rate limit exceeded for " + subject;
        return headline
                + ": " + plain(used) + "/" + plain(limit) + " " + unit
                + ". Resets in " + resetAfterSeconds + "s.";
    }

    /**
     * 纳元计数还原成金额；其余维度原样输出。
     *
     * <p>保留 8 位小数不裁剪：JSON 里 {@code 12.00000000} 与内部计数精度一致，
     * 只有 {@link #describe()} 的展示文本才去掉多余的零。</p>
     */
    private static BigDecimal amount(long value, boolean cost) {
        return cost ? BigDecimal.valueOf(value, QuotaDimension.COST_SCALE) : BigDecimal.valueOf(value);
    }

    private static String resetAt(long resetAtMillis) {
        return resetAtMillis <= 0L
                ? null
                : RESET_AT.format(Instant.ofEpochMilli(resetAtMillis).atZone(ZoneOffset.UTC));
    }

    /**
     * 距窗口重置的秒数，向上取整并至少给 1 秒——给 0 会让客户端立即重投，等于没退避。
     */
    private static long resetAfterSeconds(long resetAtMillis, long nowMillis) {
        if (resetAtMillis <= nowMillis) {
            return 1L;
        }
        return Math.max(1L, (resetAtMillis - nowMillis + 999L) / 1000L);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
