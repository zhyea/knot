package org.chobit.knot.gateway.adapter;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上游鉴权头集中注入器回归测试。
 *
 * <p>用「真实 RestClient + 请求拦截器截获请求头 + 拦截器内抛异常阻断真实网络」的方式断言，
 * 覆盖：Bearer 注入、x-api-key + 固定 anthropic-version 注入、NONE 跳过、
 * 空密钥时 Bearer 跳过、API_KEY 仍注入 anthropic-version（保持原行为）。</p>
 */
class UpstreamAuthApplierTest {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final UpstreamAuthApplier applier = new UpstreamAuthApplier();

    @Test
    void shouldInjectBearerHeader() {
        HttpHeaders headers = capture(AuthScheme.BEARER, Map.of("apiKey", "sk-123"));
        assertEquals("Bearer sk-123", headers.getFirst("Authorization"));
    }

    @Test
    void shouldInjectAnthropicApiKeyAndVersion() {
        HttpHeaders headers = capture(AuthScheme.API_KEY, Map.of("apiKey", "sk-ant"));
        assertEquals("sk-ant", headers.getFirst("x-api-key"));
        assertEquals(ANTHROPIC_VERSION, headers.getFirst("anthropic-version"));
    }

    @Test
    void shouldSkipHeadersForNoneScheme() {
        HttpHeaders headers = capture(AuthScheme.NONE, Map.of("apiKey", "sk-123"));
        assertNull(headers.getFirst("Authorization"));
        assertNull(headers.getFirst("x-api-key"));
        assertNull(headers.getFirst("anthropic-version"));
    }

    @Test
    void shouldSkipBearerWhenApiKeyBlank() {
        HttpHeaders headers = capture(AuthScheme.BEARER, Map.of("apiKey", "   "));
        assertNull(headers.getFirst("Authorization"));
    }

    @Test
    void shouldStillInjectAnthropicVersionWhenApiKeyBlank() {
        // 保持原行为：anthropic-version 始终注入，x-api-key 仅在有 key 时注入
        HttpHeaders headers = capture(AuthScheme.API_KEY, Map.of("apiKey", ""));
        assertNull(headers.getFirst("x-api-key"));
        assertEquals(ANTHROPIC_VERSION, headers.getFirst("anthropic-version"));
    }

    private HttpHeaders capture(AuthScheme scheme, Map<String, Object> authConfig) {
        List<HttpHeaders> captured = new ArrayList<>();
        RestClient client = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    captured.add(request.getHeaders());
                    throw new AbortSignal();
                })
                .build();
        UpstreamRequestContext context = new UpstreamRequestContext(
                ModelApiProtocolEnum.CHAT_COMPLETIONS,
                new LinkedHashMap<>(),
                null, null, null,
                authConfig,
                null, null, null);
        RestClient.RequestBodySpec spec = client.post().uri("http://localhost/upstream");
        applier.apply(spec, context, scheme);
        try {
            spec.body(Map.of()).retrieve().toBodilessEntity();
        } catch (AbortSignal expected) {
            // 拦截器已截获请求头，阻断真实网络调用
        }
        assertTrue(captured.size() >= 1, "未截获到请求头，测试无法断言");
        return captured.get(0);
    }

    private static final class AbortSignal extends RuntimeException {
    }
}
