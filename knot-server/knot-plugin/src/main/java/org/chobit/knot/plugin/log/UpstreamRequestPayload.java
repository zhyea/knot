package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 上游调用请求日志 payload：固定字段 + 客户端请求体。
 *
 * <p>⚠ {@code requestBody} 是<b>客户端发来</b>的请求体，<b>不是</b>供应商实际收到的请求：
 * 真正出站的体由 {@code adapter.buildRequestBody} 在 {@code UPSTREAM_REQUEST} 分发之后生成
 * （跨协议绑定时形状不同，如 Chat Completions → Bedrock Converse）。</p>
 *
 * <p>为什么不等出站体生成后再记：{@code UPSTREAM_REQUEST} 要覆盖超时、连接失败这类
 * 「请求已发出但拿不到响应」的场景，只能提前分发；代价就是拿不到适配后的出站形态。</p>
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
                                     @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode requestBody,
                                     String upstreamUrl) implements PluginLogPayload {
}
