package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;

/**
 * 上游调用结果。
 *
 * <p>{@code streamResponse} 非空表示流式转发：响应体不再落成一个字符串，
 * 而是由 {@link UpstreamStreamResponse} 持有的输入流直接转发给调用方。</p>
 *
 * <p>{@code usage} 是一次调用的完整用量账目
 * （{@link UsageAccounting}：上游原文 + 内部计费输入 + 计费结果）。</p>
 *
 * <p>{@code upstreamRequestId} 是供应商侧请求标识（响应头白名单归一），
 * 供数仓 attempt 级绑定与网关日志使用——用途是网关与供应商联合排查，不透客户端
 * （见 {@code docs/上游trace打通-双向对账设计-2026-10-17.md} §1.1）。流式路径由
 * {@link UpstreamStreamResponse} 的快照转填，非流式由执行器 exchange 时归一。</p>
 */
public record ProxyResult(String responseBody,
                          Long providerId,
                          Long modelId,
                          UsageAccounting usage,
                          UpstreamStreamResponse streamResponse,
                          String upstreamRequestId) {

    public ProxyResult(String responseBody, Long providerId, Long modelId, UsageAccounting usage) {
        this(responseBody, providerId, modelId, usage, null, null);
    }

    public boolean streaming() {
        return streamResponse != null;
    }
}
