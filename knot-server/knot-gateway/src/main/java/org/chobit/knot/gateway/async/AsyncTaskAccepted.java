package org.chobit.knot.gateway.async;

import java.util.Map;

/**
 * 异步任务提交成功的响应载体：让控制器以 <b>202 Accepted</b> 写出信封。
 *
 * <p>为什么需要一个载体而不是直接返回 {@code Map}：控制器出口 {@code ResponseEntity.ok()} 恒 200，
 * 而「任务已提交」必须是 202 —— 状态码本身就是契约的一部分（客户端据此进入轮询而不是当结果用）。
 * 用一个类型把「这是被接受的提交回执」显式表达出来，比在控制器里猜响应体形状可靠。</p>
 *
 * @param envelope 对外信封：{@code task_id} / {@code status=queued} / {@code query_url}
 */
public record AsyncTaskAccepted(Map<String, Object> envelope) {
}
