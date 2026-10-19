package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游（供应商）错误日志 payload：固定字段（含错误信息）+ 原始请求 JSON。
 *
 * <p>{@code upstreamRequestId} 是供应商侧请求 id（响应头白名单归一）：**排障工单 90% 在
 * 错误场景**，这个 id 是提工单时供应商唯一认的检索键；无命中为 null。</p>
 */
public record UpstreamErrorPayload(String providerCode,
                                   String modelCode,
                                   String protocol,
                                   String upstreamRequestId,
                                   String errorType,
                                   String errorMessage,
                                   JsonNode requestBody) implements PluginLogPayload {
}
