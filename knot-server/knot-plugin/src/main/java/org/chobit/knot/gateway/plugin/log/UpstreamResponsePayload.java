package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游（供应商）响应日志 payload：固定字段 + 原始响应 JSON。
 *
 * <p>{@code upstreamRequestId} 是供应商侧请求 id（响应头白名单归一）：提工单时
 * 供应商唯一认的检索键；无命中为 null。</p>
 *
 * <p>{@code upstreamUrl} 是本次上游调用的完整请求地址（scheme + host + path）：
 * 响应阶段与请求同值，便于三阶段日志按同一端点串联。</p>
 */
public record UpstreamResponsePayload(String providerCode,
                                      String modelCode,
                                      String protocol,
                                      String upstreamRequestId,
                                      JsonNode response,
                                      String upstreamUrl) implements PluginLogPayload {
}
