package org.chobit.knot.gateway.vo.routing;

/**
 * 路由调试协议能力项。把调试面板所需的协议行为元数据一次性下发，前端不再维护网关路径表与协议提示文案。
 *
 * <p>默认请求体不再硬编码：改由「预设请求」用例（kb_test_request_presets）维护完整、具体的请求体，
 * 调试面板按当前协议从预设中载入。本对象仅承载结构元数据：
 * <ul>
 *   <li>gatewayPath：网关调试路径（与 {@code RoutingRuleService#buildGatewayTestPath} 同源）</li>
 *   <li>hint：协议说明文案（与 {@code RoutingRuleService#PROTOCOL_DEBUG_HINTS} 同源）</li>
 *   <li>promptField：请求体中承载 prompt 的字段路径（与 {@code RoutingRuleService#promptFieldOf} 同源）</li>
 * </ul>
 *
 * <p>promptField 支持 {@code messages[0].content}、{@code input}、{@code prompt}、{@code query} 等形式；
 * 无 prompt 字段的协议（如语音转录）为 null。
 */
public record ProtocolDebugCapabilityItem(String code,
                                          String canonicalCode,
                                          String gatewayPath,
                                          String hint,
                                          String promptField) {
}
