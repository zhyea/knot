package org.chobit.knot.common.error;

/**
 * 已登录但无权访问目标资源时抛出，统一由全局异常处理器返回 HTTP 403。
 *
 * 未登录 / token 无效用 {@link UnauthorizedException}（HTTP 401），不用本异常表达。
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
