package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 网关错误日志 payload：固定字段（含错误信息）+ 原始请求 JSON。
 */
public record GatewayErrorPayload(String ruleCode,
                                  String protocol,
                                  String errorType,
                                  String errorMessage,
                                  JsonNode requestBody) implements PluginLogPayload {
}
