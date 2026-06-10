package org.chobit.knot.gateway.error;

/** 未登录或 token 无效时抛出，统一由全局异常处理器返回 HTTP 401。 */
public class UnauthorizedException extends BusinessException {

    /**
     * Constructs a new instance.
     */
    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
