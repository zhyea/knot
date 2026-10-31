package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * 网关调用异常翻译的唯一出口：4xx 复用既有错误码，连接失败 / 5xx 归为网关不可用（503）。
 *
 * <p>此前 svc 的 {@code GatewayOutageRunner} 与 admin 的 {@code GatewayAsyncTaskRunner}
 * 各写一份逐字相同的实现（含 400/401/403/404 映射表）。任一侧改错误语义，另一侧不会跟上，
 * 两个 app 对同一次网关故障会给出不同的对外错误码——而这类漂移在编译与单测里都看不见。</p>
 *
 * <p>放在 svc 而不是 common：唯一两处消费方分属 svc 与 admin，而 admin 已依赖 svc；
 * 挪到 common 会把 Spring Web 的异常类型带进最底层模块。</p>
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
