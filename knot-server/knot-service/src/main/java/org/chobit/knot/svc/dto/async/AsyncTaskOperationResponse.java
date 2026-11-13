package org.chobit.knot.svc.dto.async;

/**
 * 异步任务运维动作（终止 / 重试）的回执。
 *
 * 任务不存在才是 404，其余一律 200 + 事实：动作后的状态、是否真的改变、一句话说明。
 *
 * @param taskCode    任务句柄
 * @param status      动作执行后的状态码（{@code AsyncTaskStatusEnum.code()}）
 * @param statusLabel 状态展示名（取自枚举的 {@code label()}，管理端不另起一套文案）
 * @param changed     本次调用是否真的改变了任务状态
 * @param message     给人看的一句话：做成了什么，或未做成的原因
 * @param retryRound  重试轮次（终止动作不改变它，原样回显）
 */
public record AsyncTaskOperationResponse(String taskCode,
                                         Integer status,
                                         String statusLabel,
                                         boolean changed,
                                         String message,
                                         Integer retryRound) {
}
