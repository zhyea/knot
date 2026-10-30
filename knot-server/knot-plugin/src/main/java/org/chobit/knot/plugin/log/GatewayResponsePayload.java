package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 网关响应日志 payload：固定字段 + 原始响应 JSON。
 */
public record GatewayResponsePayload(String ruleCode,
                                     String protocol,
                                     @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode responseBody) implements PluginLogPayload {
}
