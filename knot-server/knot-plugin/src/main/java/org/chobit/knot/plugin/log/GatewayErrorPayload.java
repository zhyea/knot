package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 网关错误日志 payload：固定字段（含错误信息）+ 终态错误时的请求体快照。
 * 请求体取错误发生时的当前值：上游阶段失败时 {@code model} 已被路由目标覆盖，不再是客户端入口原文。
 */
public record GatewayErrorPayload(String ruleCode,
                                  String protocol,
                                  String errorType,
                                  String errorMessage,
                                  @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode requestBody) implements PluginLogPayload {
}
