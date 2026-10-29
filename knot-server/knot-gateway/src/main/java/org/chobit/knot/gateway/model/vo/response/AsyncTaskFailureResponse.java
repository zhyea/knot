package org.chobit.knot.gateway.model.vo.response;

import org.apache.commons.lang3.StringUtils;

/**
 * 任务失败信息，作为 {@link AsyncTaskQueryResponse#error()} 的取值。
 *
 * <p>⚠ 它与 {@code model.GatewayErrorResponse} 不是一回事：后者是「网关拒绝/出错」的
 * 统一错误体（{@code error: {message, type, code, ...}}），配 4xx/5xx；本类型描述的是
 * 「这次任务末端失败了」——HTTP 仍是 <b>200</b>，查询成功，只是任务本身的结局是 failed。</p>
 *
 * <p>{@code message} 允许为空（上游只给了错误码的场合），所以由 {@link #of} 兜底成空串，
 * 而不是把 null 发出去——客户端按字符串渲染时 null 与 "" 的差别是「显示 null」。</p>
 */
public record AsyncTaskFailureResponse(String code, String message) {

    /**
     * 由落库的错误码/错误信息构造：{@code message} 缺失时补空串。
     */
    public static AsyncTaskFailureResponse of(String code, String message) {
        return new AsyncTaskFailureResponse(code, StringUtils.defaultString(message));
    }
}
