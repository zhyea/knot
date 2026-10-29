package org.chobit.knot.svc.dto.async;

/**
 * 异步任务查询口径档案（对应 {@code kb_async_task_profiles} 一行）。
 *
 * <p>与 {@link AsyncTaskDto} 同理：不把 dal 实体吐给 gateway。</p>
 *
 * <p>⚠ <b>六个值都是表达式，不是路径</b>（2026-10-28 语义变更）：三个 {@code *Path} 是
 * <b>MVEL 取值表达式</b>，三个 {@code status*} 是 <b>MVEL 布尔表达式</b>。
 * 判据：路径语法只能表达「取哪个节点」，而终态判定问的是「这个状态算不算成功」。
 * 求值不在本层——表达式的语义由消费方解释，svc 只负责把它原样搬过去。
 * ⚠ 因此这些字段<b>不做任何清洗或校验</b>：空串与 null 是两种合法输入，
 * 归一与兜底在消费方的模型里（{@code AsyncTaskSpec}），存两层兜底必然漂移。</p>
 *
 * <p>⚠ <b>刻意不含</b>档案的 {@code id} / {@code status} / {@code remark}：前两个是管理面字段
 * （启用状态由管理端读写），消费方只按 {@code profileCode} 取可用档案；
 * {@code remark} 是人工备注，与运行时无关。</p>
 */
public record AsyncTaskProfileDto(String profileCode,
                                     String protocol,
                                     String queryMethod,
                                     String queryPath,
                                     /** MVEL 取值表达式：从创建响应取上游任务 ID */
                                     String taskIdPath,
                                     /** MVEL 取值表达式：从查询响应取状态文本 */
                                     String statusPath,
                                     /** MVEL 布尔表达式：算不算成功 */
                                     String statusSucceeded,
                                     /** MVEL 布尔表达式：算不算失败 */
                                     String statusFailed,
                                     /** MVEL 布尔表达式：算不算仍在进行；空串 = 除成功/失败外一律非终态 */
                                     String statusRunning,
                                     /** MVEL 取值表达式：终态产物位置（标量 = 单个，数组 = 多个） */
                                     String resultPath,
                                     /** 产物是对象数组时取哪个字段作为地址；空 = 元素必须是标量 */
                                     String resultUrlField,
                                     /** MVEL 取值表达式：失败原因；空 = 沿用上游顶层 message */
                                     String errorMessagePath,
                                     Integer resultExpiresDays,
                                     Integer pollIntervalMs,
                                     Integer pollBackoffBaseMs,
                                     Integer pollBackoffMaxMs,
                                     Integer maxAttempts,
                                     Long timeoutMs) {
}
