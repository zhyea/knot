package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游（供应商）请求日志 payload：固定字段 + 原始请求 JSON。
 */
public record UpstreamRequestPayload(String providerCode,
                                     String modelCode,
                                     String protocol,
                                     String bindingProtocol,
                                     String contentType,
                                     JsonNode requestBody) implements PluginLogPayload {
}
