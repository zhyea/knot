package org.chobit.knot.gateway.model.vo.response;

import org.apache.commons.lang3.StringUtils;

/**
 * 任务失败信息，作为 {@link AsyncTaskQueryResponse#error()} 的取值。
 * 与 {@code model.GatewayErrorResponse}（网关拒绝 / 出错，配 4xx/5xx）不同：本类型表示任务末端失败，HTTP 仍为 200、查询成功。
 * {@code message} 允许为空，由 {@link #of} 兜底成空串。
 */
public record AsyncTaskFailureResponse(String code, String message) {

    /**
     * 由落库的错误码/错误信息构造：{@code message} 缺失时补空串。
     */
    public static AsyncTaskFailureResponse of(String code, String message) {
        return new AsyncTaskFailureResponse(code, StringUtils.defaultString(message));
    }
}
