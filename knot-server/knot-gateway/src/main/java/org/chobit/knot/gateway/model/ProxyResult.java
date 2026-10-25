package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.adapter.model.usage.UsageAccounting;
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
 * （见 {@code docs/上游trace打通-双向对账设计-2026-10-17.md} §1.1）。</p>
 *
 * <p>{@code upstreamUrl} 是本次上游调用的<b>完整请求地址</b>
 * （{@code joinUrl(baseUrl, joinPath(model.base_path, protocolPath))}）：落 Kafka 流量事件
 * 与上游日志，排障能直接看出请求打到了哪个供应商端点（含 scheme + host + path）。</p>
 */
public record ProxyResult(String responseBody,
                          Long providerId,
                          Long modelId,
                          UsageAccounting usage,
                          UpstreamStreamResponse streamResponse,
                          String upstreamRequestId,
                          String upstreamUrl) {

    public ProxyResult(String responseBody, Long providerId, Long modelId, UsageAccounting usage) {
        this(responseBody, providerId, modelId, usage, null, null, null);
    }

    public boolean streaming() {
        return streamResponse != null;
    }
}
