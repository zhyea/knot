package org.chobit.knot.gateway.model;

import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;

/**
 * 上游调用结果。
 *
 * <p>⭐ <b>{@code responseBody} 与 {@code upstreamResponseBody} 是两份不同的东西，不许混用</b>：
 * 前者是 {@code adapter.handleResponse} 之后的<b>适配后响应</b>（客户端响应的来源，进日志即
 * {@code UPSTREAM_RESPONSE} 记的那层），后者是供应商 <b>wire 原文</b>（适配之前，只供上游日志
 * 与对账取证，不参与任何计算）。跨协议绑定（如 Anthropic Messages → OpenAI Chat）时两者
 * 的 {@code id} / {@code created} / {@code finish_reason} / {@code usage} 都不一样 ——
 * 拿适配后的去对供应商后台，必然对不上账。</p>
 *
 * <p>{@code upstreamResponseBody} 只在非流式路径有值：流式响应根本没有「整包字符串」，
 * 正文由 {@code streamResponse} 持有的 {@code Flux} 逐块转发。</p>
 *
 * <p>{@code streamResponse} 非空表示流式转发：响应体不再落成一个字符串，
 * 而是由 {@link UpstreamStreamResponse} 持有的输入流直接转发给调用方。</p>
 *
 * <p>{@code usage} 是一次调用的完整用量账目
 * （{@link UsageAccounting}：上游原文 + 内部计费输入 + 计费结果）。⚠ 其中的 {@code rawUsage}
 * 是在<b>适配后</b> body 上提取的，不是 wire 原文。</p>
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
     *
     * <p>异步提交只拿到「任务已创建」，此时的 usage 是从创建回执推出来的占位量，
     * 不是已经发生的消费。事件与限额都必须看到 {@code usage=null}，不能把占位量当账。</p>
     */
    public ProxyResult withoutUsage() {
        return new ProxyResult(responseBody, providerId, modelId, null,
                streamResponse, upstreamRequestId, upstreamUrl, upstreamResponseBody);
    }

    /**
     * 流式正文 tee 的补记出口：正文只有流读完才拿得到，那时才把 wire 快照填回结果。
     *
     * <p>⭐ 复用第 8 组件而不是加新字段：日志快照只认 {@code upstreamResponseBody} 这一个键，
     * 补记出来的结果与「一开始就有整包」的非流式结果形状完全一致，插件侧不需要分叉。</p>
     */
    public ProxyResult withUpstreamResponseBody(String body) {
        return new ProxyResult(responseBody, providerId, modelId, usage,
                streamResponse, upstreamRequestId, upstreamUrl, body);
    }
}
