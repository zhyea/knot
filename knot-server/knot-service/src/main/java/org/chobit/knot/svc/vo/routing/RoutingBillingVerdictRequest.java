package org.chobit.knot.svc.vo.routing;

import java.util.Map;

/**
 * 路由规则测试·计费裁判请求（v7 §4.1）：前端在测试响应到手后自动回传当次调用产出。
 *
 * <p>纪律：<b>不发第二次上游请求</b>——被告就是「已在抽屉里显示过的那次调用」的产出。
 * {@code knotExtend} 为网关自产的 {@code {usage, billing}}（被告账）；
 * {@code upstreamUsage} 为与 {@code knot_extend} 并列的原始 usage 原文（证据）；
 * {@code requestBody} 为请求侧计费属性（证据）。{@code targetCode}（实际承接模型，
 * 响应头 {@code X-Knot-Model}）用于定位计费规则与用量提取器。</p>
 *
 * @param modelCode     被测模型业务码
 * @param targetCode    实际承接模型（响应头 X-Knot-Model）；定位规则与提取器的依据
 * @param protocol      接口协议
 * @param stream        是否流式
 * @param knotExtend    被告账：网关自产 {usage, billing}
 * @param upstreamUsage 上游 usage 原文（证据）
 * @param requestBody   请求体（证据：size 等计费属性）
 * @param sessionId     被测请求 id（串 trace，可空）
 * @param upstreamBody  上游<b>响应体原文</b>（异步任务取网关查询响应的 {@code upstream_body}）。
 *                      ⭐ 视频类提取器从响应取输出时长与产物数，<b>只给 {@code upstreamUsage}
 *                      不够</b>；为空时退回「把 upstreamUsage 包成 {usage}」的同步口径
 * @param taskStatus    异步任务终态词（{@code completed} / {@code failed} / 在途词）；同步链路为空
 */
public record RoutingBillingVerdictRequest(String modelCode,
                                           String targetCode,
                                           String protocol,
                                           boolean stream,
                                           Map<String, Object> knotExtend,
                                           Map<String, Object> upstreamUsage,
                                           Map<String, Object> requestBody,
                                           String sessionId,
                                           Map<String, Object> upstreamBody,
                                           String taskStatus) {
}
