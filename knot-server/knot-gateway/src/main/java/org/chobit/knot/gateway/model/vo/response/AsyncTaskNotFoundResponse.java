package org.chobit.knot.gateway.model.vo.response;

/**
 * 任务查不到时的响应体（配 HTTP <b>404</b>）。
 *
 * <p><b>为什么与 {@code GatewayErrorResponse} 不统一</b>：统一错误体的 {@code error}
 * 是对象（{@code {message, type, code}}），而这条端点的 {@code error} 一直是字符串
 * （{@code {"error":"task not found: tc-1"}}）。本次只把「拼 Map」换成类型，
 * <b>不顺手改 wire format</b>——形状变更属于契约决策，得有单独的理由与公告，
 * 不该夹在「controller 瘦身」里偷偷发生。</p>
 *
 * <p>⭐ <b>为什么「查不到」和「不属于你」共用这一份</b>：见 {@code GatewayTaskController}
 * 类注释——两者都回 404 且 body 相同，是为了不泄漏「这个句柄是否存在」。</p>
 *
 * @param error 面向调用方的短句，含被查询的句柄（便于联调时对照日志）
 */
public record AsyncTaskNotFoundResponse(String error) {

    /**
     * 标准文案：句柄原样回显，客户端据此确认自己查的是哪一个。
     *
     * <p>⚠ 空句柄也走这里（{@code task not found: }）：保留旧行为，不额外区分
     * 「没传」与「传了不存在」——两者都是 404，多一种形状只是多一种客户端分支。</p>
     */
    public static AsyncTaskNotFoundResponse of(String taskCode) {
        return new AsyncTaskNotFoundResponse("task not found: " + taskCode);
    }
}
