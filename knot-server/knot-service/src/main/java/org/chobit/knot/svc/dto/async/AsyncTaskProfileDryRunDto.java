package org.chobit.knot.svc.dto.async;

/**
 * 档案试跑的入参：<b>未保存的表单</b> + 两段上游响应样本。
 *
 * <p>⭐ <b>为什么要允许「未保存」</b>：档案的表达式是贴着某家上游的响应结构写的，
 * 而那段响应只有在真实调用后才有。若只能「先保存再试」，运维就得先落一份可能是错的配置，
 * 再改一次 —— 而错误配置一旦被绑定引用，在途任务就会按它轮询。</p>
 *
 * <p>⚠ 两段响应都可为空：只贴查询响应时只验终态/产物，只贴创建响应时只验任务 ID 提取。</p>
 *
 * @param profile           待试跑的档案内容（复用写入载体，避免两套字段清单）
 * @param createResponseJson 上游「创建任务」的响应体原文
 * @param queryResponseJson  上游「查询任务」的响应体原文
 */
public record AsyncTaskProfileDryRunDto(AsyncTaskProfileSaveDto profile,
                                        String createResponseJson,
                                        String queryResponseJson) {
}
