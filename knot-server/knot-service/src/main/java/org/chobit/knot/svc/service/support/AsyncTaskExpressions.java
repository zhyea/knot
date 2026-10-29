package org.chobit.knot.svc.service.support;

/**
 * 一组异步档案表达式的聚合（六条），供求值与终态判定共用。
 *
 * <p>⭐ <b>为什么要有这个类型</b>：{@code kb_async_task_profiles} 的一行里，真正决定「怎么读懂
 * 上游响应」的是这六条表达式（三个取值 + 三个判定）。把六个字符串作为散参在方法间传递，
 * 一是每加一条口径就要改所有签名，二是调用点看不出「这六个是一组、必须同源」。</p>
 *
 * <p>⚠ 它<b>不是</b> {@code AsyncTaskProfileDto} 的替代：档案 DTO 是「一行配置」，
 * 还要带轮询参数、编码、协议；本类型只挑出求值相关的那部分，因此运行时（网关按快照）
 * 与管理面（试跑按草稿）能共用同一套判定入口，而不必互相转换整个载体。</p>
 *
 * @param statusPath        MVEL 取值表达式：从查询响应取状态文本
 * @param statusSucceeded   MVEL 布尔表达式：算不算成功
 * @param statusFailed      MVEL 布尔表达式：算不算失败
 * @param statusRunning     MVEL 布尔表达式：算不算进行中；空 = 除成功/失败外一律非终态
 * @param resultPath        MVEL 取值表达式：产物地址（标量 = 单个，数组 = 多个）
 * @param resultUrlField    数组元素是对象时取哪个字段作为产物地址；空 = 元素必须是标量
 * @param errorMessagePath  MVEL 取值表达式：失败原因；空 = 沿用上游顶层 {@code message}
 */
public record AsyncTaskExpressions(String statusPath,
                                   String statusSucceeded,
                                   String statusFailed,
                                   String statusRunning,
                                   String resultPath,
                                   String resultUrlField,
                                   String errorMessagePath) {
}
