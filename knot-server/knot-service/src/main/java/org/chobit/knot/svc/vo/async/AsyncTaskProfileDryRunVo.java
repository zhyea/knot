package org.chobit.knot.svc.vo.async;

import java.util.List;

/**
 * 档案试跑结果：把「档案怎么读这段响应」全部摊开给人看。
 * {@link #errors()} 为阻断项（表达式编译不过、判定返回非布尔、JSON 不是合法对象）；{@link #warnings()} 为提示项（能跑通但结果可疑，如状态既不匹配成功也不匹配失败且未配运行态），不拦保存。
 * {@code terminal} 为 {@code null} 表示没能判定（取不到状态或表达式有错），与「判定为 RUNNING」是两件事，前端不能渲染成同一行。
 *
 * @param upstreamTaskId 创建响应里按 {@code task_id_path} 取到的任务 ID
 * @param status         查询响应里按 {@code status_path} 取到的状态原文
 * @param terminal       三值判定（RUNNING / SUCCEEDED / FAILED）；未能判定为 null
 * @param resultUrls     按 {@code result_path} 取到的产物（可能多个）
 * @param errorMessage   按 {@code error_message_path} 取到的失败原因
 * @param elapsedHint    给人看的一句话摘要（如「命中成功，2 个产物」），前端原样展示
 */
public record AsyncTaskProfileDryRunVo(String upstreamTaskId,
                                       String status,
                                       String terminal,
                                       List<String> resultUrls,
                                       String errorMessage,
                                       String elapsedHint,
                                       List<String> errors,
                                       List<String> warnings) {
}
