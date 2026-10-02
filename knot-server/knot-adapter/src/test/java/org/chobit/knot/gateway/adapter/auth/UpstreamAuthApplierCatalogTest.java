package org.chobit.knot.gateway.adapter.auth;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴权策略族回归测试：目录按 code/类名解析（空/未知回退默认 Bearer），
 * 三个策略各自注入正确的头（Bearer / x-api-key+anthropic-version / 不注入）。
 *
 * <p>用「真实 RestClient + 拦截器截获请求头 + 抛异常阻断网络」断言，不引 Mockito。</p>
 */
class UpstreamAuthApplierCatalogTest {

    private final BearerAuthApplier bearer = new BearerAuthApplier();
    private final AnthropicApiKeyAuthApplier anthropic = new AnthropicApiKeyAuthApplier();
    private final NoAuthApplier none = new NoAuthApplier();
    private final UpstreamAuthApplierCatalog catalog =
            new UpstreamAuthApplierCatalog(List.of(bearer, anthropic, none));

    @Test
    void shouldResolveByCode() {
        assertSame(bearer, catalog.resolve("BEARER"));
        assertSame(anthropic, catalog.resolve("ANTHROPIC_API_KEY"));
        assertSame(none, catalog.resolve("NONE"));
        assertSame(bearer, catalog.resolve("bearer"), "code 应大小写不敏感");
    }

    @Test
    void shouldResolveByClassName() {
        assertSame(anthropic, catalog.resolve("AnthropicApiKeyAuthApplier"));
        assertSame(bearer, catalog.resolve(BearerAuthApplier.class.getName()));
    }

    @Test
    void shouldFallbackToDefaultForBlankOrUnknown() {
        assertSame(bearer, catalog.resolve(null));
        assertSame(bearer, catalog.resolve("  "));
        assertSame(bearer, catalog.resolve("NOPE"));
    }

    @Test
    void shouldReportSupports() {
        assertTrue(catalog.supports("BEARER"));
        assertTrue(catalog.supports("ANTHROPIC_API_KEY"));
        assertFalse(catalog.supports("NOPE"));
        assertFalse(catalog.supports(null));
    }

    @Test
    void shouldExposeDefinitions() {
        List<UpstreamAuthApplierDefinition> defs = catalog.definitions();
        assertEquals(3, defs.size());
        assertTrue(defs.stream().anyMatch(d -> "BEARER".equals(d.code())));
        assertTrue(defs.stream().anyMatch(d -> "ANTHROPIC_API_KEY".equals(d.code())));
        assertTrue(defs.stream().anyMatch(d -> "NONE".equals(d.code())));
    }

    @Test
    void bearerShouldInjectAuthorizationHeader() {
        HttpHeaders headers = capture(bearer, Map.of("apiKey", "sk-123"));
        assertEquals("Bearer sk-123", headers.getFirst(GatewayHeaders.AUTHORIZATION));
    }

    @Test
    void bearerShouldSkipWhenApiKeyBlank() {
        HttpHeaders headers = capture(bearer, Map.of("apiKey", "  "));
        assertNull(headers.getFirst(GatewayHeaders.AUTHORIZATION));
    }

    @Test
    void anthropicShouldInjectApiKeyAndVersion() {
        HttpHeaders headers = capture(anthropic, Map.of("apiKey", "sk-ant"));
        assertEquals("sk-ant", headers.getFirst(GatewayHeaders.X_API_KEY));
        assertEquals("2023-06-01", headers.getFirst(GatewayHeaders.ANTHROPIC_VERSION));
    }

    @Test
    void noneShouldInjectNothing() {
        HttpHeaders headers = capture(none, Map.of("apiKey", "sk-123"));
        assertNull(headers.getFirst(GatewayHeaders.AUTHORIZATION));
        assertNull(headers.getFirst(GatewayHeaders.X_API_KEY));
        assertNull(headers.getFirst(GatewayHeaders.ANTHROPIC_VERSION));
    }

    private HttpHeaders capture(UpstreamAuthApplier applier, Map<String, Object> authConfig) {
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
        applier.apply(spec, context);
        try {
            spec.body(Map.of()).retrieve().toBodilessEntity();
        } catch (AbortSignal expected) {
            // 截获到请求头即阻断
        }
        assertTrue(captured.size() >= 1, "未截获到请求头");
        return captured.get(0);
    }

    private static final class AbortSignal extends RuntimeException {
    }
}
