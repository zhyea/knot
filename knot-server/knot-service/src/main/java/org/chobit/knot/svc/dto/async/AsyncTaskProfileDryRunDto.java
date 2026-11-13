package org.chobit.knot.svc.dto.async;

/**
 * 档案试跑的入参：未保存的表单 + 两段上游响应样本。
 *
 * 两段响应都可为空：只贴查询响应时只验终态 / 产物，只贴创建响应时只验任务 ID 提取。
 *
 * @param profile           待试跑的档案内容（复用写入载体，避免两套字段清单）
 * @param createResponseJson 上游「创建任务」的响应体原文
 * @param queryResponseJson  上游「查询任务」的响应体原文
 */
public record AsyncTaskProfileDryRunDto(AsyncTaskProfileSaveDto profile,
                                        String createResponseJson,
                                        String queryResponseJson) {
}
