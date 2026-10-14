package org.chobit.knot.gateway.error;

/**
 * 400（Bad Request）语义的跨模块父异常。
 *
 * <p>供无法反向依赖网关异常家族的下层模块（如 knot-plugin）抛出：由网关
 * {@code GatewayExceptionHandler} 统一捕获转 HTTP 400。携带 {@code errorType} + {@code code}，
 * 便于响应体带出结构化 type/code（与网关自有异常家族同一形状）。</p>
 */
public class GatewayBadRequestException extends RuntimeException {

    private final String errorType;
    private final String code;

    public GatewayBadRequestException(String message, String errorType, String code) {
        super(message);
        this.errorType = errorType;
        this.code = code;
    }

    public String errorType() {
        return errorType;
    }

    public String code() {
        return code;
    }
}
