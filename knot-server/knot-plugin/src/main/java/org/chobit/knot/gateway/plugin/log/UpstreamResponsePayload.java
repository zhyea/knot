package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游（供应商）响应日志 payload：固定字段 + 原始响应 JSON。
 */
public record UpstreamResponsePayload(String providerCode,
                                      String modelCode,
                                      String protocol,
                                      JsonNode response) implements PluginLogPayload {
}
