package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

import org.chobit.knot.gateway.plugin.gateway.GatewayRoutingSnapshot;

/**
 * 网关请求日志 payload：固定字段 + 原始请求 JSON + 路由快照。
 */
public record GatewayRequestPayload(String ruleCode,
                                    String protocol,
                                    String contentType,
                                    JsonNode requestBody,
                                    GatewayRoutingSnapshot routing) implements PluginLogPayload {
}
