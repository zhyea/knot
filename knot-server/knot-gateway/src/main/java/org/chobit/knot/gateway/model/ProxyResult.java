package org.chobit.knot.gateway.model;

import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;

/**
 * 上游调用结果。
 * {@code responseBody} 是 {@code adapter.handleResponse} 之后的适配后响应（客户端响应的来源），{@code upstreamResponseBody} 是供应商 wire 原文（只供上游日志与对账取证，不参与计算），两者不许混用；非流式是整包，流式由正文 tee 在流读完时回填头尾双留的有界快照。
 * {@code streamResponse} 非空表示流式转发，正文由 {@link UpstreamStreamResponse} 持有的流逐块转发；{@code usage} 是一次调用的完整用量账目（{@link UsageAccounting}），其中 {@code rawUsage} 取自 wire 原文，与算账用的适配后 body 不是同一份。
 * {@code upstreamRequestId} 为供应商侧请求标识（响应头白名单归一），供数仓 attempt 级绑定与网关日志、不透客户端；{@code upstreamUrl} 为本次上游调用的完整请求地址（scheme + host + path），落 Kafka 流量事件与上游日志。
 */
public record ProxyResult(String responseBody,
                          Long providerId,
                          Long modelId,
                          UsageAccounting usage,
                          UpstreamStreamResponse streamResponse,
                          String upstreamRequestId,
                          String upstreamUrl,
                          String upstreamResponseBody) {

    public ProxyResult(String responseBody, Long providerId, Long modelId, UsageAccounting usage) {
        this(responseBody, providerId, modelId, usage, null, null, null, null);
    }

    public boolean streaming() {
        return streamResponse != null;
    }

    /**
     * 去掉本次调用的用量账目，保留上游结果本身。
     * 异步提交的 usage 是从创建回执推出的占位量，事件与限额一律按 {@code usage=null} 处理。
     */
    public ProxyResult withoutUsage() {
        return new ProxyResult(responseBody, providerId, modelId, null,
                streamResponse, upstreamRequestId, upstreamUrl, upstreamResponseBody);
    }

    /** 流式正文 tee 的补记出口：正文只有流读完才拿得到，那时把 wire 快照填回 {@code upstreamResponseBody}。 */
    public ProxyResult withUpstreamResponseBody(String body) {
        return new ProxyResult(responseBody, providerId, modelId, usage,
                streamResponse, upstreamRequestId, upstreamUrl, body);
    }
}
