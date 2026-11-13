package org.chobit.knot.svc.vo.common.meta;

/**
 * 异步档案下拉候选项的 meta（供模型表单的「任务协议」下拉使用）。
 * {@code disabled} 的语义由后端算好（{@code OptionItem#disabled}）；meta 只带选中后要回显的
 * 归属协议、查询方法与路径、轮询节奏，不带表达式与 JSON 快照。
 *
 * @param protocol       所属 API 协议编码（前端按协议过滤选项）
 * @param queryMethod    查询方法（GET / POST）
 * @param queryPath      查询路径模板（含 {@code {task_id}} 占位）
 * @param pollIntervalMs 首次轮询的基准间隔
 * @param timeoutMinutes knot 侧任务超时（分钟）
 */
public record AsyncTaskProfileMeta(String protocol,
                                   String queryMethod,
                                   String queryPath,
                                   Integer pollIntervalMs,
                                   Long timeoutMinutes) {
}
