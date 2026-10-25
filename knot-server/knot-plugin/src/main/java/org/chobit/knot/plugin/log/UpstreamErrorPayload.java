package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游（供应商）错误日志 payload：固定字段（含错误信息）+ 原始请求 JSON。
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
                                   JsonNode requestBody,
                                   String upstreamUrl) implements PluginLogPayload {
}
