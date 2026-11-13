package org.chobit.knot.svc.dto.async;

/**
 * 异步任务查询口径档案（对应 {@code kb_async_task_profiles} 一行）。
 *
 * 六个值都是表达式而非路径：三个 {@code *Path} 是 MVEL 取值表达式，三个 {@code status*} 是 MVEL 布尔表达式。
 * 这些字段不做清洗或校验，空串与 null 是两种合法输入，归一与兜底在消费方（{@code AsyncTaskSpec}）。
 * 不含 {@code id} / {@code status} / {@code remark} 等管理面字段；消费方按 {@code profileCode} 取可用档案。
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
                                     Long timeoutMinutes,
                                     /** 查询响应取证：从查询响应取上游 usage 对象的 MVEL 表达式；空 = 沿用顶层 usage */
                                     String usagePath) {
}
