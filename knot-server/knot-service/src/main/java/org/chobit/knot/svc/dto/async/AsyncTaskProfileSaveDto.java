package org.chobit.knot.svc.dto.async;

/**
 * 异步任务档案的写入载体（新建与更新共用）。
 *
 * {@code profileCode} 在更新时不可修改：它是绑定表引用的键（{@code kb_model_api_bindings.async_profile_code}），
 * 换编码只能新建 + 迁移绑定。归一（首尾空白、协议 / 方法大写）与校验在 {@code AsyncTaskProfileSupport}。
 */
public record AsyncTaskProfileSaveDto(String profileCode,
                                      String protocol,
                                      String queryMethod,
                                      String queryPath,
                                      /** MVEL 取值表达式：创建响应 → 上游任务 ID */
                                      String taskIdPath,
                                      /** MVEL 取值表达式：查询响应 → 状态文本 */
                                      String statusPath,
                                      /** MVEL 布尔表达式：算成功 */
                                      String statusSucceeded,
                                      /** MVEL 布尔表达式：算失败 */
                                      String statusFailed,
                                      /** MVEL 布尔表达式：仍在进行；空 = 除成功/失败外一律非终态 */
                                      String statusRunning,
                                      /** MVEL 取值表达式：产物地址（标量或数组） */
                                      String resultPath,
                                      /** 产物是对象数组时取哪个字段（如 url）；空 = 元素必须是标量 */
                                      String resultUrlField,
                                      /** MVEL 取值表达式：失败原因；空 = 沿用顶层 message */
                                      String errorMessagePath,
                                      Integer resultExpiresDays,
                                      Integer pollIntervalMs,
                                      Integer pollBackoffBaseMs,
                                      Integer pollBackoffMaxMs,
                                      Integer maxAttempts,
                                      Long timeoutMinutes,
                                      /** 启用状态码（EnabledStatusEnum）；新建时 null 视为启用 */
                                      Integer status,
                                      String remark,
                                      /** 查询响应取证：从查询响应取上游 usage 对象的 MVEL 表达式（可空）；空 = 沿用顶层 usage */
                                      String usagePath) {
}
