package org.chobit.knot.gateway.service.support;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 网关 DECISIONS 端点的真实调用实现（dogfooding：复用网关鉴权 / 路由 / 计费）。
 *
 * <p>与被测调用同实例：基地址由管理端 {@code GatewayRuntimeProperties.normalizedBaseUrl()} 透传。
 * 裁判凭据仅写入 {@code Authorization: Bearer <secretKey>} 头；请求体只放 state / questions /
 * provider / session_id / trace——凭据不进请求体、不进日志、不下前端（v7 §6.1 纪律）。</p>
 */
@Service
public class RestClientDecisionsTransport implements DecisionsTransport {

    /** 网关 DECISIONS 路径（与 {@code GatewayDecisionsController} 一致） */
    private static final String DECISIONS_PATH = "/v1/decisions";
    /** 管理端与被测网关同机/同网段，短连接超时即可 */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    /** DECISIONS 判官调用可能数百 ms 至数秒，读取超时放宽到 30s */
    private static final int READ_TIMEOUT_MS = 30_000;

    private final RestClient restClient;

    public RestClientDecisionsTransport() {
        this.restClient = RestClientSupport.withTimeouts(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    @Override
    public String post(String gatewayBaseUrl, String bearerToken, String ruleCode, Map<String, Object> body) {
        String base = normalizeBaseUrl(gatewayBaseUrl);
        // 凭据仅进鉴权头；body 不含任何 secret
        String authorization = "Bearer " + (bearerToken == null ? "" : bearerToken.trim());
        return restClient.post()
                .uri(base + DECISIONS_PATH)
                .header(GatewayHeaders.AUTHORIZATION, authorization)
                .header(GatewayHeaders.RULE, ruleCode)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String base = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:9090" : baseUrl.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
