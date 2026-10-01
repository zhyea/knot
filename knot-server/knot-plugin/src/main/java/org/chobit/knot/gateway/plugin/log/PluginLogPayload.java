package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.chobit.knot.gateway.plugin.log.GatewayErrorPayload;
import org.chobit.knot.gateway.plugin.log.GatewayRequestPayload;
import org.chobit.knot.gateway.plugin.log.GatewayResponsePayload;
import org.chobit.knot.gateway.plugin.log.UpstreamErrorPayload;
import org.chobit.knot.gateway.plugin.log.UpstreamRequestPayload;
import org.chobit.knot.gateway.plugin.log.UpstreamResponsePayload;

/**
 * 插件日志 payload 的统一出口。
 *
 * <p>原先 payload 是 {@code Map<String, Object>}，字段集合和类型都由字符串键决定，日志消费者
 * 无法依赖稳定结构。这里改为受限的六种固定 payload record：
 * 每个阶段只携带该阶段真正有的字段（网关日志不带 provider 字段，上游日志不带 routing 字段，
 * 错误日志才带错误信息），请求/响应体等原始 JSON 用 {@code JsonNode} 原样保存。
 *
 * <p>序列化时通过 {@code payloadType} 判别六种类型，禁止消费者依赖字符串键猜测类型。
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
