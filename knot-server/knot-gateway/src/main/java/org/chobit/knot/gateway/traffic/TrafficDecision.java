package org.chobit.knot.gateway.traffic;

/**
 * 一次流量判定的结果。
 *
 * <p>{@code allowed=false} 时带上被拒原因、命中的资源、限额与当前用量、窗口重置时刻，
 * 供网关拼错误信息，也方便后续往响应头里透出 {@code Retry-After}。</p>
 */
public record TrafficDecision(boolean allowed,
                              TrafficRejectReason reason,
                              String resourceType,
                              Long resourceId,
                              long limit,
                              long used,
                              long resetAtMillis) {

    private static final TrafficDecision ALLOWED = new TrafficDecision(true, null, null, null, 0L, 0L, 0L);

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
     * @param resourceId    命中的资源 ID
     * @param limit         限额
     * @param used          当前用量（已含本次）
     * @param resetAtMillis 计数重置时刻
     */
    public static TrafficDecision reject(TrafficRejectReason reason,
                                         String resourceType,
                                         Long resourceId,
                                         long limit,
                                         long used,
                                         long resetAtMillis) {
        return new TrafficDecision(false, reason, resourceType, resourceId, limit, used, resetAtMillis);
    }
}
