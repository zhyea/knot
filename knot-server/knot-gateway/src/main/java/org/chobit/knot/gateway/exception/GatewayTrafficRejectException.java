package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.model.TrafficRejectDetail;

/**
 * 流量被拒绝：限流（RPM / TPM）与限额（token / 成本）都归这一类，
 * 统一由网关异常处理器映射成 HTTP 429，并带上 {@code Retry-After} 与结构化明细。
 *
 * <p>两者只有错误类型与业务码不同（{@code rate_limit_error} / {@code quota_error}），
 * 状态码与响应形状完全一致，因此共用同一个处理器出口。</p>
 */
public abstract class GatewayTrafficRejectException extends GatewayRequestException {

    private final TrafficRejectDetail detail;

    protected GatewayTrafficRejectException(String message, String type, String code, TrafficRejectDetail detail) {
        super(message, type, code);
        this.detail = detail;
    }

    /**
     * 被拒明细：资源、维度、上限、已用量与重置时刻。
     */
    public TrafficRejectDetail detail() {
        return detail;
    }
}
