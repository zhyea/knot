package org.chobit.knot.svc.dto.routing;

import org.chobit.knot.adapter.model.usage.NormalizedUsage;

import java.time.Instant;
import java.util.Map;

/**
 * {@link BillingFactAssembler} 的组装入参：被测调用的全部可观测输入。
 *
 * <p>与网关生产链路同口径取数（提取器 + 三级回退 + 归一化），但发生在管理端、离线、不影响响应。
 * 字段与网关 {@code UsageExtractorRegistry.extract} 一一对应，便于对照。</p>
 *
 * @param modelCode          被测模型业务码
 * @param modelFamilyCode    模型族码（三级回退第二级；可空）
 * @param ruleCode           模型显式绑定的规则码（三级回退第一级；可空）
 * @param extractorCode      用量提取器码（如 DEFAULT-TOKEN；可空 → 默认提取器）
 * @param protocol           接口协议
 * @param stream             是否流式
 * @param upstreamRawBody    上游响应体（含 usage 字段的 Map），提取计费输入
 * @param upstreamRawUsage   上游原始 usage（并列透传，仅供对照）
 * @param requestBody        本次请求体（像素 / 声音等请求侧计费属性）
 * @param gatewayBilling     被告账：网关实际注入的 knot_extend.billing（可空）
 * @param upstreamUsage      上游 usage 原文（用于抽取上游 cost；可为 upstreamRawUsage 本身）
 * @param occurredAt         发生时点（高低峰判定；可空 → 取当前）
 * @param taskStatus         异步任务终态词（{@code completed} / {@code failed} / 在途词）；同步链路为
 *                           {@code null}。只用于「成功必有账、失败不得有账」那一条自检
 */
public record BillingFactAssemblyInput(String modelCode,
                                       String modelFamilyCode,
                                       String ruleCode,
                                       String extractorCode,
                                       String protocol,
                                       boolean stream,
                                       Map<String, Object> upstreamRawBody,
                                       Map<String, Object> upstreamRawUsage,
                                       Map<String, Object> requestBody,
                                       NormalizedUsage gatewayBilling,
                                       Map<String, Object> upstreamUsage,
                                       Instant occurredAt,
                                       String taskStatus) {
}
