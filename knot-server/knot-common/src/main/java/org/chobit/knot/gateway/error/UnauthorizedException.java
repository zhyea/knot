package org.chobit.knot.gateway.error;

/** 未登录或 token 无效时抛出，全局异常处理器应返回 HTTP 401。 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
