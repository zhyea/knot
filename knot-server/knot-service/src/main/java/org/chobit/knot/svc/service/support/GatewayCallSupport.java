package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * 网关调用异常翻译的唯一出口：4xx 复用既有错误码，连接失败 / 5xx 归为网关不可用（503）。
 */
public final class GatewayCallSupport {

    private GatewayCallSupport() {
    }

    /**
     * 执行一次网关调用并翻译异常。
     *
     * @param action 实际调用
     * @param op     操作名（进错误信息，便于定位是哪个动作失败）
     */
    public static <T> T call(ThrowingSupplier<T> action, String op) {
        try {
            return action.get();
        } catch (HttpStatusCodeException ex) {
            HttpStatusCode status = ex.getStatusCode();
            ErrorCode code = switch (status.value()) {
                case 400 -> ErrorCode.VALIDATION_ERROR;
                case 401 -> ErrorCode.UNAUTHORIZED;
                case 403 -> ErrorCode.FORBIDDEN;
                case 404 -> ErrorCode.NOT_FOUND;
                default -> ErrorCode.GATEWAY_UNAVAILABLE;
            };
            throw new BusinessException(code,
                    op + " 失败：网关返回 HTTP " + status.value() + " " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.GATEWAY_UNAVAILABLE,
                    op + " 失败：无法连接网关（" + ex.getMessage() + "）");
        }
    }

    /**
     * 受检异常包装（RestClient 的调用可能抛受检异常）。
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
