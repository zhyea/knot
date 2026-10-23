package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 非流式路径的上游响应头捕获（executor 捕获点一）。
 *
 * <p>直接驱动 {@code readBuffered}：端到端 WebClient 会拖 UsageExtractorRegistry 整条
 * 依赖链（GatewayDataService / 计费规则查询），而这里要验的只是「body 与头分流」这一件事。</p>
 *
 * <p>对应设计文档 §4.2 捕获点三的兄弟分支：非流式失败同样要带供应商 id——
 * 排障工单 90% 发生在错误场景。只取 request-id，不取 log-id（见设计文档 §4.1）。</p>
 */
class AbstractUpstreamProtocolExecutorBufferedTest {

    private static ClientResponse.Builder response(String body, HttpStatus status) {
        return ClientResponse.create(status).body(body);
    }

    @Test
    void successCarriesUpstreamRequestId() {
        ClientResponse response = response("{\"ok\":true}", HttpStatus.OK)
                .header("x-request-id", "req_openai")
                .build();

        AbstractUpstreamProtocolExecutor.BufferedExchange exchange =
                AbstractUpstreamProtocolExecutor.readBuffered(response, null).block();

        assertEquals("{\"ok\":true}", exchange.body());
        assertEquals("req_openai", exchange.upstreamRequestId());
    }

    @Test
    void successWithoutWhitelistHeadersYieldsNullIds() {
        ClientResponse response = response("{\"ok\":true}", HttpStatus.OK)
                .header("set-cookie", "session=secret")
                .build();

        AbstractUpstreamProtocolExecutor.BufferedExchange exchange =
                AbstractUpstreamProtocolExecutor.readBuffered(response, null).block();

        assertNull(exchange.upstreamRequestId());
    }

    @Test
    void failureCarriesUpstreamRequestIdAndStatus() {
        ClientResponse response = response("{\"error\":\"busy\"}", HttpStatus.SERVICE_UNAVAILABLE)
                .header("x-request-id", "req_failed")
                .header("Retry-After", "30")
                .build();

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> AbstractUpstreamProtocolExecutor.readBuffered(response, null).block());

        assertEquals(503, error.httpStatus());
        assertEquals("{\"error\":\"busy\"}", error.responseBody());
        assertEquals("req_failed", error.upstreamRequestId(),
                "失败场景的供应商 id 是工单里最关键的检索键");
        assertEquals(30_000L, error.retryAfterMs(), "Retry-After 解析不回归");
    }

    @Test
    void failureWithoutWhitelistHeadersYieldsNullIds() {
        ClientResponse response = response("{\"error\":\"boom\"}", HttpStatus.BAD_GATEWAY).build();

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> AbstractUpstreamProtocolExecutor.readBuffered(response, null).block());

        assertEquals(502, error.httpStatus());
        assertNull(error.upstreamRequestId());
    }

    @Test
    void clientErrorIsAlsoAnUpstreamFailureNotRetriedAsSuccess() {
        ClientResponse response = response("{\"error\":\"bad request\"}", HttpStatus.BAD_REQUEST)
                .header("apim-request-id", "req_azure_400")
                .build();

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> AbstractUpstreamProtocolExecutor.readBuffered(response, null).block());

        assertEquals(400, error.httpStatus());
        assertEquals("req_azure_400", error.upstreamRequestId());
    }

    @Test
    void messageFormatMatchesStreamingPath() {
        ClientResponse response = response("{}", HttpStatus.INTERNAL_SERVER_ERROR).build();

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> AbstractUpstreamProtocolExecutor.readBuffered(response, null).block());

        assertEquals("upstream responded with status 500", error.getMessage());
    }

    /**
     * 上游把我方 {@code X-Request-Id} 原样回显时，不得当成 {@code upstream_request_id}：
     * 那是我们自己的编号，冒充供应商 id 会让工单检索与数仓对账双双失效。
     */
    @Test
    void echoedOwnRequestIdIsNotTreatedAsUpstreamId() {
        String own = "9f2c7d1e4b6a48c093e5f0a1b2c3d4e5";
        ClientResponse response = response("{\"ok\":true}", HttpStatus.OK)
                .header("x-request-id", own)
                .build();

        AbstractUpstreamProtocolExecutor.BufferedExchange exchange =
                AbstractUpstreamProtocolExecutor.readBuffered(response, own).block();

        assertNull(exchange.upstreamRequestId(), "回显值不算上游 id");
    }
}
