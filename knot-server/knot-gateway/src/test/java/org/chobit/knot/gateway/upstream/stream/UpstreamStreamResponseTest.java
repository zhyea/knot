package org.chobit.knot.gateway.upstream.stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式响应的上游头快照契约：构造时抓取、close 后仍可读。
 *
 * <p>对应设计文档 §4.2 捕获点二与 §8 风险表「流式路径 close 后头失效」——
 * 快照必须与底层连接的生命周期解耦，否则流写完后（{@code ProxyStreamingBody} finally）
 * 再取供应商 id 只会拿到空。</p>
 */
class UpstreamStreamResponseTest {

    private static UpstreamStreamResponse stream(Map<String, String> headers,
                                                 String requestId) {
        return new UpstreamStreamResponse(
                new ByteArrayInputStream("data: {}\n\n".getBytes(StandardCharsets.UTF_8)),
                MediaType.TEXT_EVENT_STREAM,
                null, null, noopResponse(),
                headers, requestId);
    }

    @Test
    void snapshotIsReadableBeforeAndAfterClose() throws IOException {
        UpstreamStreamResponse response = stream(Map.of("x-request-id", "req_openai"), "req_openai");

        assertEquals("req_openai", response.upstreamRequestId());
        response.close();

        // close 之后快照依然可读：数仓埋点在流写完后才执行
        assertEquals("req_openai", response.upstreamRequestId());
        assertEquals("req_openai", response.upstreamHeaders().get("x-request-id"));
    }

    @Test
    void nullSnapshotDegradesToEmptyMap() {
        UpstreamStreamResponse response = stream(null, null);

        assertTrue(response.upstreamHeaders().isEmpty());
        assertEquals(null, response.upstreamRequestId());
    }

    @Test
    void snapshotIsUnmodifiable() {
        UpstreamStreamResponse response = stream(Map.of("x-request-id", "req_openai"), "req_openai");

        assertThrows(UnsupportedOperationException.class,
                () -> response.upstreamHeaders().put("x-request-id", "tampered"));
    }

    @Test
    void legacyConstructorLeavesIdsNull() throws IOException {
        UpstreamStreamResponse legacy = new UpstreamStreamResponse(
                new ByteArrayInputStream(new byte[0]),
                MediaType.APPLICATION_JSON,
                null, null, noopResponse());

        assertTrue(legacy.upstreamHeaders().isEmpty());
        assertEquals(null, legacy.upstreamRequestId());
    }

    private static ClientHttpResponse noopResponse() {
        return new ClientHttpResponse() {
            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatus.OK;
            }

            @Override
            public String getStatusText() {
                return "OK";
            }

            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                return new org.springframework.http.HttpHeaders();
            }

            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public void close() {
            }
        };
    }
}
