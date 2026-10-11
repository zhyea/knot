package org.chobit.knot.gateway.traffic;

/**
 * 一次流量判定的结果。
 *
 * <p>{@code allowed=false} 时带上被拒原因、命中的资源、限额与当前用量、窗口重置时刻，
 * 供网关拼错误信息与响应头 {@code Retry-After}。计数键仍用资源主键 id，
 * 但对外只用业务码 {@code resourceCode}——主键是部署相关的内部标识，调用方无从对应。</p>
 *
 * @param currency 被拒维度为成本时所用的币种，其余维度为 {@code null}
 */
public record TrafficDecision(boolean allowed,
                              TrafficRejectReason reason,
                              String resourceType,
                              String resourceCode,
                              long limit,
                              long used,
                              long resetAtMillis,
                              String currency) {

    private static final TrafficDecision ALLOWED = new TrafficDecision(true, null, null, null, 0L, 0L, 0L, null);

    /**
     * 放行。
     */
    public static TrafficDecision allow() {
        return ALLOWED;
    }

    /**
     * 拒绝。
     *
     * @param reason        被拒原因
     * @param resourceType  命中的资源类型
     * @param resourceCode  命中的资源业务码
     * @param limit         限额（成本维度为纳元计数）
     * @param used          当前用量（已含本次；成本维度为纳元计数）
     * @param resetAtMillis 计数重置时刻
     * @param currency      成本维度的币种，其余维度传 {@code null}
     */
    public static TrafficDecision reject(TrafficRejectReason reason,
                                         String resourceType,
                                         String resourceCode,
                                         long limit,
                                         long used,
                                         long resetAtMillis,
                                         String currency) {
        return new TrafficDecision(false, reason, resourceType, resourceCode, limit, used, resetAtMillis, currency);
    }
}
