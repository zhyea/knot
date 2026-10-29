package org.chobit.knot.svc.dto.async;

/**
 * 异步任务档案的<b>写入</b>载体（新建与更新共用）。
 *
 * <p>⚠ <b>为什么更新也用全量载体，而不是「补丁」</b>：档案是一组必须自洽的口径
 * （路径 → 取值 → 判定 → 产物），只改其中一条表达式的场景几乎不存在，而补丁语义会引入
 * 「null 表示不改」与「null 表示清空」的歧义 —— 可选字段（{@code status_running} /
 * {@code error_message_path}）恰恰需要「清空」这个动作。</p>
 *
 * <p>⚠ {@code profileCode} 在更新时<b>不可修改</b>：它是绑定表引用的键（{@code kb_model_api_bindings.async_profile_code}），
 * 改它等于让所有引用瞬间指向不存在的档案。要换编码只能新建 + 迁移绑定。</p>
 *
 * <p>⭐ 归一（首尾空白、协议/方法大写）与校验都在 {@code AsyncTaskProfileSupport} 里完成，
 * 本类型只是「表单长什么样」的声明，不含逻辑。</p>
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
                                      Long timeoutMs,
                                      /** 启用状态码（EnabledStatusEnum）；新建时 null 视为启用 */
                                      Integer status,
                                      String remark) {
}
