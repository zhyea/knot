package org.chobit.knot.svc.service.support;

/**
 * 一组异步档案表达式的聚合（六条），供求值与终态判定共用。
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
