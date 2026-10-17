package org.chobit.knot.gateway.service.support;

import java.util.Map;

/**
 * 网关 {@code POST /v1/decisions} 调用通道（接口化以便单测替换）。
 *
 * <p>真实实现 {@link RestClientDecisionsTransport} 走 RestClient；测试用假实现注入，
 * 断言「secretKey 仅进 Authorization 头、不进请求体、不落日志」以及错误码降级。</p>
 */
public interface DecisionsTransport {

    /**
     * 调用网关 DECISIONS 端点。
     *
     * @param gatewayBaseUrl 网关基地址（已归一化）
     * @param bearerToken   裁判消费者 secretKey（仅用于鉴权头，实现不得写入日志或请求体）
     * @param ruleCode      裁判规则 code（走 Rule 请求头）
     * @param body          请求体（state + questions + provider + session_id + trace）
     * @return 网关响应体原文（2xx）
     * @throws org.springframework.web.client.HttpStatusCodeException 非 2xx（交由调用方按状态码降级）
     * @throws Exception 连接等基础设施异常（交由调用方降级）
     */
    String post(String gatewayBaseUrl, String bearerToken, String ruleCode, Map<String, Object> body);
}
