package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游（供应商）错误日志 payload：固定字段（含错误信息）+ 原始请求 JSON。
 */
public record UpstreamErrorPayload(String providerCode,
                                   String modelCode,
                                   String protocol,
                                   String errorType,
                                   String errorMessage,
                                   JsonNode requestBody) implements PluginLogPayload {
}
