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
 */
public record ProxyResult(String responseBody,
                          Long providerId,
                          Long modelId,
                          UsageAccounting usage,
                          UpstreamStreamResponse streamResponse) {

    public ProxyResult(String responseBody, Long providerId, Long modelId, UsageAccounting usage) {
        this(responseBody, providerId, modelId, usage, null);
    }

    public boolean streaming() {
        return streamResponse != null;
    }
}
