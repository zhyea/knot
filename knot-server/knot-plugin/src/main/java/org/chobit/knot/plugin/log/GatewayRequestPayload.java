package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.chobit.knot.plugin.gateway.GatewayRoutingSnapshot;

/**
 * 网关请求日志 payload：固定字段 + 客户端入口请求体 + 路由快照。
 * 请求体是路由前的客户端原文，不是发给供应商的那份（{@code model} 尚未被路由目标覆盖）。
 */
public record GatewayRequestPayload(String ruleCode,
                                    String protocol,
                                    String contentType,
                                    @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode requestBody,
                                    GatewayRoutingSnapshot routing) implements PluginLogPayload {
}
