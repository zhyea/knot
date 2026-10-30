package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.chobit.knot.plugin.gateway.GatewayRoutingSnapshot;

/**
 * 网关请求日志 payload：固定字段 + 原始请求 JSON + 路由快照。
 */
public record GatewayRequestPayload(String ruleCode,
                                    String protocol,
                                    String contentType,
                                    @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode requestBody,
                                    GatewayRoutingSnapshot routing) implements PluginLogPayload {
}
