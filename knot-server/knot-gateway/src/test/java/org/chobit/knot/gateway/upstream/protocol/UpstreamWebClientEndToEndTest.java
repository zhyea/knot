package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.adapter.auth.BearerAuthApplier;
import org.chobit.knot.gateway.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.config.UpstreamWebClientConfiguration;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上游执行器的端到端回归：真实 WebClient（reactor-netty）打真实本地服务端。
 *
 * <p>2026-10-20 上游客户端由 RestClient 迁 WebClient 后，单测只能覆盖到「WebClient 行为」
 * 与「桥接」两截，中间这条链路（鉴权头注入 → 路径拼接 → 响应头白名单 → 流式句柄）
 * 只有端到端跑一遍才算数。这里用真实服务端把它钉住：</p>
 * <ul>
 *     <li>非流式：整包响应 + 供应商 id 捕获；</li>
 *     <li>流式：响应体句柄可用、内容完整、在途释放语义不变；</li>
 *     <li>非 2xx：状态码 / Retry-After / 供应商 id 一律带出（failover 与退避的输入）；</li>
 *     <li>鉴权头：{@code HttpHeaders} 解耦后仍然落到真实请求上。</li>
 * </ul>
 */
class UpstreamWebClientEndToEndTest {

    /** 我方 request_id 的固定样本：断言上游收到的头与请求上下文里的值逐字符一致。 */
    private static final String OWN_REQUEST_ID = "9f2c7d1e4b6a48c093e5f0a1b2c3d4e5";

    private DisposableServer server;
    private ConnectionProvider connectionProvider;
    private final AtomicReference<String> capturedAuthorization = new AtomicReference<>();
    private final AtomicReference<String> capturedRequestId = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();

    @BeforeEach
    void startFakeUpstream() {
        server = HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .post("/buffered", (request, response) -> {
                            capturedAuthorization.set(request.requestHeaders().get("Authorization"));
                            capturedRequestId.set(request.requestHeaders().get("X-Request-Id"));
                            return response.header("x-request-id", "req_ok")
                                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                    .sendString(Mono.just("{\"ok\":true}"));
                        })
                        // 模型级 base_path 前缀：验证最终 URL 真的带上了它，而不是只拼在工具函数里
                        .post("/ep-abc/buffered", (request, response) -> {
                            capturedPath.set(request.fullPath());
                            return response.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                    .sendString(Mono.just("{\"ok\":true}"));
                        })
                        // 回显我方 X-Request-Id：模拟 APIM / 网关类中间件的常见行为
                        .post("/echo", (request, response) -> {
                            capturedRequestId.set(request.requestHeaders().get("X-Request-Id"));
                            return response
                                    .header("x-request-id", request.requestHeaders().get("X-Request-Id"))
                                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                    .sendString(Mono.just("{\"ok\":true}"));
                        })
                        .post("/stream", (request, response) -> response
                                .header("x-request-id", "req_sse")
                                .header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                                .sendString(Flux.just("data: {\"choices\":[]}\n\n", "data: [DONE]\n\n")))
                        .post("/boom", (request, response) -> response.status(429)
                                .header("Retry-After", "7")
                                .header("x-request-id", "req_429")
                                .sendString(Mono.just("{\"error\":\"rate limited\"}"))))
                .bindNow();
    }

    @AfterEach
    void stopFakeUpstream() {
        if (connectionProvider != null) {
            connectionProvider.dispose();
        }
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    void bufferedResponseCarriesBodyAndUpstreamRequestId() {
        ProxyResult result = executor().execute(context("/buffered", Map.of("model", "gpt")), adapter("/buffered"));

        assertEquals("{\"ok\":true}", result.responseBody());
        assertEquals("req_ok", result.upstreamRequestId());
        assertEquals("Bearer sk-test", capturedAuthorization.get(), "鉴权头必须落到真实请求上");
    }

    @Test
    void streamResponseCarriesUpstreamBytesToTheCaller() {
        ProxyResult result = executor().execute(context("/stream", Map.of("stream", true)), adapter("/stream"));

        UpstreamStreamResponse stream = result.streamResponse();
        assertNotNull(stream, "流式请求必须返回响应体句柄而不是整包字符串");
        assertTrue(stream.isEventStream());
        assertEquals("req_sse", stream.upstreamRequestId());

        String pumped = stream.body()
                .map(buffer -> buffer.toString(StandardCharsets.UTF_8))
                .collectList()
                .map(parts -> String.join("", parts))
                .block();
        assertEquals("data: {\"choices\":[]}\n\ndata: [DONE]\n\n", pumped,
                "上游字节必须原样到达调用方（批2 起响应体直接是 Flux<DataBuffer>，没有管道桥接）");
    }

    @Test
    void upstreamFailureCarriesStatusRetryAfterAndRequestId() {
        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> executor().execute(context("/boom", Map.of()), adapter("/boom")));

        assertEquals(429, error.httpStatus());
        assertEquals(7_000L, error.retryAfterMs());
        assertEquals("req_429", error.upstreamRequestId());
        assertEquals("{\"error\":\"rate limited\"}", error.responseBody());
    }

    @Test
    void requestIdReachesUpstreamAsRequestHeader() {
        ProxyResult result = executor().execute(context("/buffered", Map.of("model", "gpt")), adapter("/buffered"));

        assertEquals(OWN_REQUEST_ID, capturedRequestId.get(),
                "网关自生成的 request_id 必须以 X-Request-Id 落到真实上游请求上");
        assertEquals("req_ok", result.upstreamRequestId(),
                "上游返回的 id 与 sent 的 id 是两个字段，各归各位");
    }

    @Test
    void echoedOwnRequestIdIsNotReportedAsUpstreamRequestId() {
        // 上游把我方 X-Request-Id 原样回显：不得当成 upstream_request_id（那是对方编号）
        ProxyResult result = executor().execute(context("/echo", Map.of("model", "gpt")), adapter("/echo"));

        assertEquals(OWN_REQUEST_ID, capturedRequestId.get());
        assertNull(result.upstreamRequestId(), "回显值不构成供应商侧标识");
    }

    @Test
    void modelBasePathIsPrefixedToUpstreamPath() {
        ProxyResult result = executor().execute(
                context("/buffered", Map.of("model", "gpt"), "/ep-abc"), adapter("/buffered"));

        assertEquals("/ep-abc/buffered", capturedPath.get(),
                "base_path 必须真的进入发出的 URL，而不是只拼在工具函数里");
        assertEquals("{\"ok\":true}", result.responseBody());
    }

    private TextGenerationProtocolExecutor executor() {
        GatewayUpstreamClientProperties properties = new GatewayUpstreamClientProperties();
        UpstreamWebClientConfiguration configuration = new UpstreamWebClientConfiguration();
        connectionProvider = configuration.upstreamConnectionProvider(properties);
        HttpClient httpClient = configuration.upstreamHttpClient(connectionProvider, properties);
        WebClient webClient = configuration.upstreamWebClient(WebClient.builder(), httpClient);
        UsageExtractorRegistry registry = new UsageExtractorRegistry(null, null) {
            @Override
            public UsageAccounting extract(String responseBody,
                                           UpstreamRequestContext context,
                                           UpstreamRequestAdapter adapter) {
                return null;
            }
        };
        UpstreamAuthApplierCatalog catalog = new UpstreamAuthApplierCatalog(List.of(new BearerAuthApplier()));
        return new TextGenerationProtocolExecutor(webClient, registry, catalog, properties);
    }

    private UpstreamRequestContext context(String path, Map<String, Object> body) {
        return context(path, body, null);
    }

    private UpstreamRequestContext context(String path, Map<String, Object> body, String basePath) {
        ModelEntity model = new ModelEntity();
        model.setBasePath(basePath);
        // 上游地址归供应商账户（base_url 已从模型挪到账户，见 ProviderAccountEntity）
        ProviderAccountEntity provider = new ProviderAccountEntity();
        provider.setBaseUrl("http://127.0.0.1:" + server.port());
        return new UpstreamRequestContext(
                ModelApiProtocolEnum.CHAT_COMPLETIONS,
                body,
                MediaType.APPLICATION_JSON,
                model,
                provider,
                Map.of("apiKey", "sk-test"),
                null,
                null,
                "trace-end-to-end",
                null,
                OWN_REQUEST_ID);
    }

    private static UpstreamRequestAdapter adapter(String path) {
        return new UpstreamRequestAdapter() {
            @Override
            public String code() {
                return "END_TO_END";
            }

            @Override
            public String label() {
                return "end-to-end";
            }

            @Override
            public Set<ModelApiProtocolEnum> protocol() {
                return Set.of(ModelApiProtocolEnum.CHAT_COMPLETIONS);
            }

            @Override
            public Object buildRequestBody(UpstreamRequestContext context) {
                return context.requestBody();
            }

            @Override
            public String resolvePath(UpstreamRequestContext context, String defaultPath) {
                return path;
            }
        };
    }
}
