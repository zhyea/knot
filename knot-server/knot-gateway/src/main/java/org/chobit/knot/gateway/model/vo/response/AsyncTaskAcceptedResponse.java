package org.chobit.knot.gateway.model.vo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 异步任务提交被接受时的<b>响应体</b>（配 HTTP 202），也是「这是提交回执，不是结果」的控制信号。
 *
 * <p>⭐ <b>为什么是一个 record 而不是 {@code Map<String, Object>}</b>（2026-10-29 robin 拍板）：
 * 一份对外契约用 Map 拼，编译器就什么都不知道——字段名写错（{@code taskId} / {@code queryUrl}）、
 * 少写一个字段、把上游响应里同名字段顺手塞进来，全部只能靠人眼与联调发现，错的那一侧
 * 还是客户端的线上代码。用类型表达之后，这三件事分别由编译器、{@code final} 字段与
 * 「没有多余槽位」在编译期挡住。</p>
 *
 * <p><b>为什么控制器要靠这个类型判 202</b>：控制器出口 {@code ResponseEntity.ok()} 恒 200，
 * 而「任务已提交」必须是 202 —— 状态码本身就是契约的一部分（客户端据此进入轮询而不是当结果用）。
 * 类型同时承担「响应体形状」与「该回 202」两件事，是因为它们本就不可分：能序列化出这个 body
 * 的场合只有一种，就是提交被接受。</p>
 *
 * <p><b>wire format 归这里定义，不归调用方</b>：{@code task_id} / {@code query_url} 是蛇形，
 * 而 Java 侧是驼峰，靠 {@link JsonProperty} 钉住。⚠ 删掉注解不会有任何编译/测试信号，
 * 客户端却会突然收到 {@code taskCode} / {@code queryUrl} —— 由
 * {@code AsyncTaskAcceptedResponseTest} 断言序列化后的键名守住。</p>
 *
 * @param taskCode knot 生成的任务句柄（32 位 hex），对外字段名 {@code task_id}；
 *                 ⚠ 不是上游任务 ID，上游 ID 不外泄
 * @param status   对外状态，提交成功这里恒为 {@link #STATUS_QUEUED}
 * @param queryUrl 轮询地址，由 {@link #TASKS_PATH} 拼出，与 {@code GatewayTaskController} 同源；
 *                 ⚠ 客户端须用 <b>POST</b> 打这个地址（无请求体，句柄在路径上），GET 会 405
 *                 —— 见 {@code 10-backend-java.md §5.2}
 */
public record AsyncTaskAcceptedResponse(@JsonProperty("task_id") String taskCode,
                                       String status,
                                       @JsonProperty("query_url") String queryUrl) {

    /**
     * 客户端任务端点的路径前缀。
     *
     * <p>⭐ 提交回执里的 {@code query_url} 与 {@code GatewayTaskController} 的映射必须永远一致——
     * 否则客户端拿着一个 404 的地址去轮询，而服务端两边都没报错。唯一来源放这里，
     * 控制器直接引用它（{@code @RequestMapping} 接受编译期常量）。</p>
     */
    public static final String TASKS_PATH = "/openai/v1/tasks";

    /** 对外状态：已入队、尚未查询过（对应库里的 {@code PENDING}） */
    public static final String STATUS_QUEUED = "queued";

    /** 提交成功回执：三字段的取值关系固定，调用方不需要（也不应该）自己拼 */
    public static AsyncTaskAcceptedResponse queued(String taskCode) {
        return new AsyncTaskAcceptedResponse(taskCode, STATUS_QUEUED, TASKS_PATH + "/" + taskCode);
    }
}
