package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游（供应商）请求日志 payload：固定字段 + 原始请求 JSON。
 *
 * <p>{@code upstreamRequestId} 在本阶段恒为 null（上游尚未响应，没有 id 可记），
 * 字段占位是为三个阶段的日志行格式一致，便于按行解析。</p>
 */
public record UpstreamRequestPayload(String providerCode,
                                     String modelCode,
                                     String protocol,
                                     String bindingProtocol,
                                     String contentType,
                                     String upstreamRequestId,
                                     JsonNode requestBody) implements PluginLogPayload {
}
