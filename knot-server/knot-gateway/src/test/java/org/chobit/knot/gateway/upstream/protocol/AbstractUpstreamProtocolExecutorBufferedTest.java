package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 非流式路径的上游响应头捕获（executor 捕获点一）。
 *
 * <p>直接驱动 {@code readBuffered}：端到端 RestClient 会拖 UsageExtractorRegistry 整条
 * 依赖链（GatewayDataService / 计费规则查询），而这里要验的只是「body 与头分流」这一件事。</p>
 *
 * <p>对应设计文档 §4.2 捕获点三的兄弟分支：非流式失败同样要带供应商 id——
 * 排障工单 90% 发生在错误场景。只取 request-id，不取 log-id（见设计文档 §4.1）。</p>
 */
class AbstractUpstreamProtocolExecutorBufferedTest {

    private static MockClientHttpResponse response(String body, HttpStatus status) {
        return new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
    }

    @Test
    void successCarriesUpstreamRequestId() throws IOException {
        MockClientHttpResponse response = response("{\"ok\":true}", HttpStatus.OK);
        response.getHeaders().add("x-request-id", "req_openai");

        AbstractUpstreamProtocolExecutor.BufferedExchange exchange =
                AbstractUpstreamProtocolExecutor.readBuffered(response);

        assertEquals("{\"ok\":true}", exchange.body());
        assertEquals("req_openai", exchange.upstreamRequestId());
    }

    @Test
    void successWithoutWhitelistHeadersYieldsNullIds() throws IOException {
        MockClientHttpResponse response = response("{\"ok\":true}", HttpStatus.OK);
        response.getHeaders().add("set-cookie", "session=secret");

        AbstractUpstreamProtocolExecutor.BufferedExchange exchange =
                AbstractUpstreamProtocolExecutor.readBuffered(response);

        assertNull(exchange.upstreamRequestId());
    }

    @Test
    void failureCarriesUpstreamRequestIdAndStatus() {
        MockClientHttpResponse response = response("{\"error\":\"busy\"}", HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().add("x-request-id", "req_failed");
        response.getHeaders().add("Retry-After", "30");

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> AbstractUpstreamProtocolExecutor.readBuffered(response));

        assertEquals(503, error.httpStatus());
        assertEquals("{\"error\":\"busy\"}", error.responseBody());
        assertEquals("req_failed", error.upstreamRequestId(),
                "失败场景的供应商 id 是工单里最关键的检索键");
        assertEquals(30_000L, error.retryAfterMs(), "Retry-After 解析不回归");
    }

    @Test
    void failureWithoutWhitelistHeadersYieldsNullIds() {
        MockClientHttpResponse response = response("{\"error\":\"boom\"}", HttpStatus.BAD_GATEWAY);

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> AbstractUpstreamProtocolExecutor.readBuffered(response));

        assertEquals(502, error.httpStatus());
        assertNull(error.upstreamRequestId());
    }

    @Test
    void clientErrorIsAlsoAnUpstreamFailureNotRetriedAsSuccess() {
        MockClientHttpResponse response = response("{\"error\":\"bad request\"}", HttpStatus.BAD_REQUEST);
        response.getHeaders().add("apim-request-id", "req_azure_400");

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> AbstractUpstreamProtocolExecutor.readBuffered(response));

        assertEquals(400, error.httpStatus());
        assertEquals("req_azure_400", error.upstreamRequestId());
    }

    @Test
    void messageFormatMatchesStreamingPath() {
        MockClientHttpResponse response = response("{}", HttpStatus.INTERNAL_SERVER_ERROR);

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> AbstractUpstreamProtocolExecutor.readBuffered(response));

        assertEquals("upstream responded with status 500", error.getMessage());
    }
}
