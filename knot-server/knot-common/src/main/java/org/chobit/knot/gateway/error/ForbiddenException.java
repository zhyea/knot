package org.chobit.knot.gateway.error;

/**
 * 已登录但无权访问目标资源时抛出，统一由全局异常处理器返回 HTTP 403。
 *
 * <p>与 {@link UnauthorizedException}（未登录 / token 无效，HTTP 401）区分：
 * 前端把 401 视为「登录已过期」并强制登出，因此授权失败必须用 403 表达，
 * 否则一个接口级授权失败会把用户踢出登录态。</p>
 */
public class ForbiddenException extends BusinessException {

    /**
     * Constructs a new instance.
     */
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
