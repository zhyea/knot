package org.chobit.knot.gateway.async;

/**
 * 重建计费与事件所需的最小上下文（落 {@code kb_async_tasks.context_json}）。
 *
 * <p><b>刻意不含 API key</b>：终态只需要「这笔是按哪条规则、哪个模型、哪次请求发起的」，
 * 不需要再走一次鉴权。密钥不落任务表，避免随任务留存与外泄。</p>
 *
 * <p>由此带来一处已知缺口：终态流量事件无法回填路由维度（app / consumer / dept），
 * 因为 {@code ResolvedRouting} 需要 API key 才能重新解析。见方案文档「待办」——
 * 与「宁可少一个维度，也不把密钥写进任务表」的取舍一致。</p>
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
                               String upstreamUrl) {
}
