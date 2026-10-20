package org.chobit.knot.gateway.upstream.protocol;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 {@link WebClient} 的两个流式契约，避免迁移时踩坑。
 *
 * <p>坑一：{@code exchangeToMono} 在转换函数返回的 Mono 完成后会调用
 * {@code ClientResponse.releaseBody()}，未消费的响应体会被吞掉，流式<b>不能</b>走它；
 * {@code retrieve().toEntityFlux(...)} 才会在响应头到达时就返回、且 body 仍可订阅。
 * 本测试用真实 reactor-netty 服务端验证「响应头已拿到之后 body 依然读得到全部内容」。</p>
 *
 * <p>坑二：非 2xx 必须经 {@code onStatus} 显式映射成自定义异常，否则默认抛
 * {@code WebClientResponseException}，拿不到「供应商侧请求 id + Retry-After + 错误体」的完整上下文。</p>
 */
class UpstreamWebClientStreamingTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void toEntityFluxKeepsBodyReadableAfterHeaders() {
        DisposableServer server = sseServer();
        try {
            WebClient client = WebClient.builder().baseUrl(baseUrl(server)).build();
            ResponseEntity<Flux<DataBuffer>> entity = client.post()
                    .uri("/stream")
                    .retrieve()
                    .toEntityFlux(DataBuffer.class)
                    .block(TIMEOUT);

            assertNotNull(entity, "响应头应在 body 消费前就绪");
            assertEquals(HttpStatusCode.valueOf(200), entity.getStatusCode());
            assertEquals(MediaType.TEXT_EVENT_STREAM, entity.getHeaders().getContentType());

            String body = DataBufferUtils.join(entity.getBody() == null ? Flux.empty() : entity.getBody())
                    .map(buffer -> buffer.toString(StandardCharsets.UTF_8))
                    .block(TIMEOUT);
            assertEquals("data: a\n\ndata: b\n\ndata: c\n\n", body, "body 必须完整可读，不能被 releaseBody 吞掉");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void onStatusMapsNonSuccessToCustomException() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/boom",
                        (request, response) -> response.status(429)
                                .header("Retry-After", "3")
                                .sendString(Mono.just("upstream exploded"))))
                .bindNow();
        try {
            WebClient client = WebClient.builder().baseUrl(baseUrl(server)).build();
            Throwable thrown = null;
            try {
                client.post()
                        .uri("/boom")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                                .flatMap(text -> Mono.error(new IllegalStateException("mapped:" + text))))
                        .toEntityFlux(DataBuffer.class)
                        .block(TIMEOUT);
            } catch (Throwable ex) {
                thrown = ex;
            }
            assertNotNull(thrown, "非 2xx 必须抛异常");
            String rendered = renderChain(thrown);
            assertTrue(rendered.contains("mapped:upstream exploded"), "自定义异常应携带错误体，实际：" + rendered);
        } finally {
            server.disposeNow();
        }
    }

    private static DisposableServer sseServer() {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/stream", (request, response) -> response
                        .header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .sendString(Flux.just("data: a\n\n", "data: b\n\n", "data: c\n\n"))))
                .bindNow();
    }

    private static String baseUrl(DisposableServer server) {
        return "http://127.0.0.1:" + server.port();
    }

    private static String renderChain(Throwable thrown) {
        StringBuilder builder = new StringBuilder();
        Throwable current = thrown;
        while (current != null) {
            builder.append(current.getClass().getSimpleName()).append(':').append(current.getMessage()).append(" | ");
            current = current.getCause() == current ? null : current.getCause();
        }
        return builder.toString();
    }
}
