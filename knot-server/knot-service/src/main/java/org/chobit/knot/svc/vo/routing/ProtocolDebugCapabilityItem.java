package org.chobit.knot.svc.vo.routing;

/**
 * 路由调试协议能力项，一次性下发调试面板所需的协议行为元数据，前端无需维护网关路径表与协议提示文案。
 * 默认请求体由「预设请求」用例（{@code kb_test_request_presets}）维护，调试面板按当前协议载入；本对象仅承载结构元数据。
 * {@code gatewayPath} 为网关调试路径（与 {@code RoutingRuleService#buildGatewayTestPath} 同源）、
 * {@code hint} 为协议说明文案（与 {@code RoutingRuleService#PROTOCOL_DEBUG_HINTS} 同源）、
 * {@code promptField} 为承载 prompt 的字段路径（与 {@code RoutingRuleService#promptFieldOf} 同源）。
 * {@code promptField} 支持 {@code messages[0].content}、{@code input}、{@code prompt}、{@code query} 等形式，
 * 无 prompt 字段的协议（如语音转录）为 {@code null}。
 */
public record ProtocolDebugCapabilityItem(String code,
                                          String canonicalCode,
                                          String gatewayPath,
                                          String hint,
                                          String promptField) {
}
