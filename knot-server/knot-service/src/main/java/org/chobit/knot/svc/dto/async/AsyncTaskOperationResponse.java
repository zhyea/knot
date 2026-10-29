package org.chobit.knot.svc.dto.async;

/**
 * 异步任务运维动作（终止 / 重试）的回执。
 *
 * <p>⭐ <b>为什么住在 {@code svc.dto.async} 而不是网关或管理端</b>：这份形状有一个生产者
 * （网关 {@code /_admin/async-tasks}）和一个再次下发者（管理端转发给浏览器）。放在任一端，
 * 另一端要么反向依赖同级模块（admin → gateway 是禁止的方向），要么复制一份同形状的 record
 * ——后者就是「同一个契约两份定义」，改字段时必然漂移。svc 是两个 app 唯一的共同祖先，
 * 与 {@link AsyncTaskDto} 同包也顺理成章：它们描述的是同一张表的两种视角。</p>
 *
 * <p>⭐ <b>为什么是「状态 + 是否生效 + 一句话」而不是抛异常</b>：两个动作都会遇到
 * 「不是错误，但也没做成」的情形——任务已是终态、任务正被轮询节点处理、另一个运维刚重试过。
 * 做成 4xx 只能让管理端弹一个红条，而运维真正需要的是<b>当前状态</b>与「要不要再点一次」。
 * 所以：任务不存在才是 404，其余一律 200 + 事实。</p>
 *
 * @param taskCode    任务句柄
 * @param status      动作执行后的状态码（{@code AsyncTaskStatusEnum.code()}）
 * @param statusLabel 状态展示名（取自枚举的 {@code label()}，管理端不另起一套文案）
 * @param changed     本次调用是否真的改变了任务状态
 * @param message     给人看的一句话：做成了什么，或为什么没做
 * @param retryRound  重试轮次（终止动作不改变它，原样回显）
 */
public record AsyncTaskOperationResponse(String taskCode,
                                         Integer status,
                                         String statusLabel,
                                         boolean changed,
                                         String message,
                                         Integer retryRound) {
}
