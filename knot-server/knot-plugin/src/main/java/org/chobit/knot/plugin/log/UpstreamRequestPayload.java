package org.chobit.knot.plugin.log;

import org.chobit.knot.plugin.PluginBodySourceEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 上游调用请求日志 payload：固定字段 + 客户端请求体。
 *
 * {@code requestBody} 是路由目标覆盖后的请求体：{@code model} 已换成该候选的上游模型编码，其余字段与客户端请求体一致。
 * 它仍不是供应商真正收到的出站体——出站体由 {@code adapter.buildRequestBody} 在 {@code UPSTREAM_REQUEST} 分发之后生成。
 * {@code upstreamRequestId} 在本阶段恒为 {@code null}（占位保持三阶段日志行格式一致）。
 * {@code upstreamUrl} 是本次上游调用的完整地址（scheme + host + path）；{@code bodySource} 恒为 {@link PluginBodySourceEnum#CLIENT}。
 */
public record UpstreamRequestPayload(String providerCode,
                                     String modelCode,
                                     String protocol,
                                     String bindingProtocol,
                                     String contentType,
                                     String upstreamRequestId,
                                     @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode requestBody,
                                     String upstreamUrl,
                                     PluginBodySourceEnum bodySource) implements PluginLogPayload {
}
