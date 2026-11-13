package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 网关响应日志 payload：固定字段 + 网关最终响应投影。
 * 响应体是客户端实收的那一层（适配后；开启用量详情时含 {@code knot_extend}），流式响应没有 SSE 正文。
 */
public record GatewayResponsePayload(String ruleCode,
                                     String protocol,
                                     @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode responseBody) implements PluginLogPayload {
}
