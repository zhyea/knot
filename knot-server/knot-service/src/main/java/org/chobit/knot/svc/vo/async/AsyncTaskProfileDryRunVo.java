package org.chobit.knot.svc.vo.async;

import java.util.List;

/**
 * 档案试跑结果：把「档案怎么读这段响应」全部摊开给人看。
 *
 * <p>⭐ <b>为什么 errors 与 warnings 分开</b>：</p>
 * <ul>
 *   <li>{@link #errors()}：<b>阻断</b>——表达式编译不过、判定返回非布尔、JSON 不是合法对象。
 *       这些在运行时会让任务判错或直接抛，配置必须改。</li>
 *   <li>{@link #warnings()}：<b>提示</b>——能跑通，但结果看起来可疑。典型是「上游状态既不匹配成功、
 *       也不匹配失败，且没配运行态」，运行时会一直轮询到超时。它不该拦住保存
 *       （也许这就是在配一个中间态），但必须让人看见。</li>
 * </ul>
 *
 * <p>⚠ {@code terminal} 为 {@code null} 表示<b>没能判定</b>（响应里取不到状态，或表达式有错）。
 * 这与「判定为 RUNNING」是两件事，前端不能把两者渲染成同一行。</p>
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
