package org.chobit.knot.gateway.upstream.stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式转发与「在途计数释放」的生命周期。
 *
 * <p>要点：请求线程在首字节后即返回，模型在途计数必须由响应体<b>流写完</b>才释放；
 * 且客户端断连 / 上游中断（抛 IOException）时同样要释放，否则计数泄漏会让该模型长期被判为繁忙。</p>
 */
class ProxyStreamingBodyTest {

    @Test
    void releasesInflightAfterEventStreamFinished() throws IOException {
        AtomicInteger released = new AtomicInteger();
        String payload = "data: {\"choices\":[]}" + System.lineSeparator() + "data: [DONE]" + System.lineSeparator();
        ProxyStreamingBody body = new ProxyStreamingBody(
                stream(payload, true), null, false, 8192, released::incrementAndGet);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("[DONE]"));
        assertEquals(1, released.get(), "SSE 流写完必须释放一次在途计数");
    }

    @Test
    void releasesInflightAfterRawStreamFinished() throws IOException {
        AtomicInteger released = new AtomicInteger();
        ProxyStreamingBody body = new ProxyStreamingBody(
                stream("hello-stream", false), null, false, 8192, released::incrementAndGet);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);

        assertEquals("hello-stream", out.toString(StandardCharsets.UTF_8));
        assertEquals(1, released.get());
    }

    @Test
    void releasesInflightEvenWhenPumpFails() {
        AtomicInteger released = new AtomicInteger();
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("client disconnected");
            }
        };
        ProxyStreamingBody body = new ProxyStreamingBody(
                new UpstreamStreamResponse(failing, MediaType.TEXT_EVENT_STREAM, null, null, noopResponse()),
                null, false, 8192, released::incrementAndGet);

        try {
            body.writeTo(new ByteArrayOutputStream());
        } catch (IOException expected) {
            // 客户端断连：异常照常上抛给容器
        }

        assertEquals(1, released.get(), "中途失败也必须释放，否则在途计数泄漏");
    }

    @Test
    void tolerateMissingCompletionCallback() throws IOException {
        ProxyStreamingBody body = new ProxyStreamingBody(stream("x", false), null, false, 8192, null);
        body.writeTo(new ByteArrayOutputStream());
        // 无回调时不应抛 NPE
    }

    private static UpstreamStreamResponse stream(String payload, boolean eventStream) {
        return new UpstreamStreamResponse(
                new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
                eventStream ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON,
                null, null, noopResponse());
    }

    private static ClientHttpResponse noopResponse() {
        return new ClientHttpResponse() {
            @Override
            public org.springframework.http.HttpStatusCode getStatusCode() {
                return org.springframework.http.HttpStatus.OK;
            }

            @Override
            public String getStatusText() {
                return "OK";
            }

            @Override
            public void close() {
                // no-op
            }

            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                return new org.springframework.http.HttpHeaders();
            }
        };
    }
}
