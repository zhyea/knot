package org.chobit.knot.gateway.service.support;

import java.util.Map;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 路由规则调试链路的 HTTP 出口：持有调试专用 {@link RestClient}，负责向网关发起真实请求。
 *
 * <p>与业务 Service 分离的原因是可测性与职责单一边界——本类不含校验、不查库、不翻译结果，
 * 只做「发出去」和「把响应交回去」。</p>
 */
@Component
public class RoutingRuleDebugRunner {

    /** 调试链路连接网关的超时 */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    /** 流式读取的空闲超时（按相邻数据间隔计算，不限制整包总时长） */
    private static final int READ_IDLE_TIMEOUT_MS = 120_000;

    private final RestClient restClient;

    public RoutingRuleDebugRunner() {
        this.restClient = RestClientSupport.withTimeouts(CONNECT_TIMEOUT_MS, READ_IDLE_TIMEOUT_MS);
    }

    /**
     * 同步调用：返回网关原始响应体。4xx/5xx 抛 {@code HttpStatusCodeException}，
     * 由调用方翻译成测试结果而非在此吞掉状态码。
     */
    public String invoke(String baseUrl,
                         String gatewayPath,
                         String secretKey,
                         String ruleCode,
                         ModelApiProtocolEnum protocol,
                         Map<String, Object> body) {
        RestClient.RequestBodySpec request = restClient.post()
                .uri(baseUrl + gatewayPath)
                .header("Authorization", "Bearer " + secretKey)
                .header("Rule", ruleCode)
                .header("traceparent", RoutingRuleDebugSupport.TRACEPARENT);
        if (ModelApiProtocolEnum.IMAGE_EDITS == protocol) {
            return request.contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(RoutingRuleDebugSupport.toMultipartBody(body))
                    .retrieve()
                    .body(String.class);
        }
        return request.contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /**
     * 流式调用：返回<b>未关闭</b>的响应体供增量读取，调用方负责关闭。
     *
     * <p>{@code exchange(fn, false)} 不自动关闭响应：既不应用默认错误处理器
     * （网关 4xx/5xx 需要原样读取响应体），也让响应体保持可增量读取。</p>
     */
    public ClientHttpResponse openStream(String baseUrl,
                                        String gatewayPath,
                                        String secretKey,
                                        String ruleCode,
                                        Map<String, Object> body) {
        return restClient.post()
                .uri(baseUrl + gatewayPath)
                .header("Authorization", "Bearer " + secretKey)
                .header("Rule", ruleCode)
                .header("traceparent", RoutingRuleDebugSupport.TRACEPARENT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.ALL)
                .body(body)
                .exchange((clientRequest, clientResponse) -> clientResponse, false);
    }
}
