package org.chobit.knot.svc.vo.common.meta;

/**
 * 异步档案下拉候选项的 meta（供模型表单的「任务协议」下拉使用）。
 *
 * <p>⭐ {@code disabled} 的语义由<b>后端</b>算好（{@code OptionItem#disabled}），这里只带
 * 「选中之后要回显什么」：归属协议、查询方法与路径、轮询节奏。前端据此渲染一行灰字，
 * 让人看清自己到底绑了个什么，而不是面对一个裸编码。</p>
 *
 * <p>⚠ 不带表达式与 JSON 快照：下拉里没人读它们，带出去只会让「档案能不能改」的边界变模糊。</p>
 *
 * @param protocol       所属 API 协议编码（前端按协议过滤选项）
 * @param queryMethod    查询方法（GET / POST）
 * @param queryPath      查询路径模板（含 {@code {task_id}} 占位）
 * @param pollIntervalMs 首次轮询的基准间隔
 * @param timeoutMs      knot 侧任务超时
 */
public record AsyncTaskProfileMeta(String protocol,
                                   String queryMethod,
                                   String queryPath,
                                   Integer pollIntervalMs,
                                   Long timeoutMs) {
}
