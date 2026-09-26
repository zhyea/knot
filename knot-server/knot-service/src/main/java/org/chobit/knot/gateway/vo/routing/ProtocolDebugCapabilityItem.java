package org.chobit.knot.gateway.vo.routing;

import java.util.Map;

/**
 * 路由调试协议能力项。把调试面板所需的协议行为元数据一次性下发，
 * 前端不再维护网关路径表、协议提示文案、默认请求体模板与 prompt 字段推断。
 *
 * <p>所有取值均复用 {@code RoutingRuleService} 调试执行链的同一批私有方法
 * （{@code buildGatewayTestPath} / {@code defaultRequestBody}），保证「预览的请求」
 * 与「实际发出的请求」永远一致。model 与 prompt 在模板中以 {@code {{model}}} /
 * {@code {{prompt}}} 占位，由前端按需填充。
 *
 * <p>promptField 为请求体中承载 prompt 的字段路径，支持 {@code messages[0].content}、
 * {@code input}、{@code prompt}、{@code query} 等形式；无 prompt 字段的协议（如语音转录）为 null。
 */
public record ProtocolDebugCapabilityItem(String code,
                                          String canonicalCode,
                                          String gatewayPath,
                                          String hint,
                                          Map<String, Object> defaultRequestBody,
                                          String promptField) {
}
