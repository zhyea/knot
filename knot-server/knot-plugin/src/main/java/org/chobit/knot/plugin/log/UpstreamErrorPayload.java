package org.chobit.knot.plugin.log;

import org.chobit.knot.plugin.PluginBodySourceEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 上游（供应商）错误日志 payload：固定字段（含错误信息）+ 客户端请求体 + 上游错误证据。
 *
 * {@code httpStatus} / {@code upstreamResponseBody} 仅在「失败来自上游响应」时有值，超时 / 连接失败为 {@code null}；
 * 后者是供应商 wire 原文（不是适配后响应），经 {@link PluginLogBodySerializer} 按截断策略落日志。
 * {@code requestBody} 是客户端发来的请求体，不是供应商实际收到的；{@code upstreamRequestId} 是供应商侧请求 id，无命中为 {@code null}。
 * {@code upstreamUrl} 是本次上游调用的完整地址（scheme + host + path）；{@code bodySource} 恒为 {@link PluginBodySourceEnum#CLIENT}。
 */
public record UpstreamErrorPayload(String providerCode,
                                   String modelCode,
                                   String protocol,
                                   String upstreamRequestId,
                                   String errorType,
                                   String errorMessage,
                                   @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode requestBody,
                                   String upstreamUrl,
                                   Integer httpStatus,
                                   @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode upstreamResponseBody,
                                   PluginBodySourceEnum bodySource) implements PluginLogPayload {
}
