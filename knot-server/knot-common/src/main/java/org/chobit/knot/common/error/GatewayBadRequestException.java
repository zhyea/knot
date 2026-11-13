package org.chobit.knot.common.error;

/**
 * 400（Bad Request）语义的跨模块父异常，供无法反向依赖网关异常家族的下层模块（如 knot-plugin）抛出。
 *
 * 由网关 {@code GatewayExceptionHandler} 捕获转 HTTP 400；携带 {@code errorType} + {@code code}，响应体带出结构化 type/code。
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
