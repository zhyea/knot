package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游（供应商）请求日志 payload：固定字段 + 原始请求 JSON。
 *
 * <p>{@code upstreamRequestId} 在本阶段恒为 null（上游尚未响应，没有 id 可记），
 * 字段占位是为三个阶段的日志行格式一致，便于按行解析。</p>
 *
 * <p>{@code upstreamUrl} 是本次上游调用的完整请求地址（scheme + host + path）：
 * 请求阶段在请求发起前已可算出，供日志直接呈现「请求打到了哪个供应商端点」。</p>
 */
public record UpstreamRequestPayload(String providerCode,
                                     String modelCode,
                                     String protocol,
                                     String bindingProtocol,
                                     String contentType,
                                     String upstreamRequestId,
                                     JsonNode requestBody,
                                     String upstreamUrl) implements PluginLogPayload {
}
