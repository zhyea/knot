package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 网关响应日志 payload：固定字段 + 原始响应 JSON。
 */
public record GatewayResponsePayload(String ruleCode,
                                     String protocol,
                                     JsonNode responseBody) implements PluginLogPayload {
}
