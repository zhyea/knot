package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 插件日志 payload 的统一出口：六种固定 payload record，每个阶段只携带该阶段真正有的字段，
 * 请求 / 响应体等原始 JSON 用 {@code JsonNode} 原样保存。
 *
 * 序列化时以 {@code payloadType} 判别类型，消费者不得依赖字符串键猜测类型。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "payloadType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GatewayRequestPayload.class, name = "gateway-request"),
        @JsonSubTypes.Type(value = GatewayResponsePayload.class, name = "gateway-response"),
        @JsonSubTypes.Type(value = GatewayErrorPayload.class, name = "gateway-error"),
        @JsonSubTypes.Type(value = UpstreamRequestPayload.class, name = "provider-request"),
        @JsonSubTypes.Type(value = UpstreamResponsePayload.class, name = "provider-response"),
        @JsonSubTypes.Type(value = UpstreamErrorPayload.class, name = "provider-error")
})
public sealed interface PluginLogPayload
        permits GatewayRequestPayload, GatewayResponsePayload, GatewayErrorPayload,
                UpstreamRequestPayload, UpstreamResponsePayload, UpstreamErrorPayload {
}
