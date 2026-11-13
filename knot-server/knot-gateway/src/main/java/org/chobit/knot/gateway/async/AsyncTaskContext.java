package org.chobit.knot.gateway.async;

/**
 * 重建计费与事件所需的最小上下文（落 {@code kb_async_tasks.context_json}）。
 * 不含 API key；终态只需规则、模型与请求维度，密钥不落任务表。
 * 终态流量事件不回填路由维度（app / consumer / dept）。
 * 资源主键单独留存，只服务终态限额 / 成本记账。
 */
public record AsyncTaskContext(String taskCode,
                               String ruleCode,
                               String stickyKey,
                               String traceparent,
                               String requestId,
                               String protocol,
                               String modelCode,
                               String upstreamModelCode,
                               String providerAccountCode,
                               String poolCode,
                               String upstreamUrl,
                               Long routingRuleId,
                               Long consumerId,
                               Long appId,
                               Long targetModelId,
                               /** 提交时快照：消费者是否要看用量明细；终态/回调据此决定要不要下发账目。旧快照缺该字段 = false */
                               boolean returnUsageDetail) {
}
