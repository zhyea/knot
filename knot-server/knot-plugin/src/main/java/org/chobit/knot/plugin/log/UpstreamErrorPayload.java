package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 上游（供应商）错误日志 payload：固定字段（含错误信息）+ 客户端请求体 + 上游错误证据。
 *
 * <p>⭐ {@code httpStatus} / {@code upstreamResponseBody} 是<b>排障的主证据</b>：上游 4xx/5xx 的
 * 真实状态码与错误体只存在于调用侧异常里（{@code GatewayUpstreamException}），
 * 不落到日志就只剩一句「upstream responded with status 400」，供应商工单根本没法提。
 * 两者仅在「失败来自上游响应」时有值，超时 / 连接失败为 null。</p>
 *
 * <p>⚠ {@code upstreamResponseBody} 是供应商 wire 原文（<b>不是</b>适配后响应），
 * 同样经 {@link PluginLogBodySerializer} 走截断策略：错误体常含回显的 prompt，不能整段落盘。</p>
 *
 * <p>{@code requestBody} 是<b>客户端发来</b>的请求体，不是供应商实际收到的（见
 * {@link UpstreamRequestPayload} 的同名说明）。</p>
 *
 * <p>{@code upstreamRequestId} 是供应商侧请求 id（响应头白名单归一）：**排障工单 90% 在
 * 错误场景**，这个 id 是提工单时供应商唯一认的检索键；无命中为 null。</p>
 *
 * <p>{@code upstreamUrl} 是本次上游调用的完整请求地址（scheme + host + path）：
 * 失败候选也带，failover 排障能直接看出失败的是哪个具体端点。</p>
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
                                   @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode upstreamResponseBody) implements PluginLogPayload {
}
