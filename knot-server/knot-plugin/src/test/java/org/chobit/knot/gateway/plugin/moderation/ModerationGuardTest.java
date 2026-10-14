package org.chobit.knot.gateway.plugin.moderation;

import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModerationGuardTest {

    private static final String RESPONSE_BODY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"here is badword\"}}]}";

    private static ModerationTerm word(String text, String categoryCode, int severity) {
        return new ModerationTerm(text, TextNormalizer.normalize(text), categoryCode, severity, null);
    }

    private static ModerationProperties properties(boolean enabled, String action, String scope) {
        ModerationProperties properties = new ModerationProperties();
        properties.setEnabled(enabled);
        properties.setAction(action);
        properties.setScope(scope);
        return properties;
    }

    private static ModerationSnapshotCache cache(List<ModerationTerm> words) {
        return new ModerationSnapshotCache(new ModerationWordSource() {
            @Override
            public List<ModerationTerm> loadTerms() {
                return words;
            }

            @Override
            public long currentVersion() {
                return words.stream()
                        .map(ModerationTerm::updatedAtMillis)
                        .filter(java.util.Objects::nonNull)
                        .mapToLong(Long::longValue)
                        .max()
                        .orElse(0L);
            }
        }, 0L);
    }

    private static ModerationGuard guard(ModerationProperties properties, List<ModerationTerm> words) {
        return new ModerationGuard(properties, cache(words), new ModerationEventSink());
    }

    private static GatewayPluginContext requestContext(Map<String, Object> requestBody) {
        return context(PluginStageCode.GATEWAY_REQUEST, requestBody, null);
    }

    private static GatewayPluginContext responseContext(Object responseBody) {
        return context(PluginStageCode.GATEWAY_RESPONSE, Map.of(), responseBody);
    }

    private static GatewayPluginContext context(PluginStageCode stage,
                                                Map<String, Object> requestBody,
                                                Object responseBody) {
        return new GatewayPluginContext("trace-1", null, stage, "key-1", "rule-a",
                "openai", null, requestBody, null, responseBody, null);
    }

    private static Map<String, Object> requestBody() {
        return Map.of("messages", List.of(Map.of("role", "user", "content", "say badword")));
    }

    @Test
    void blocksRequestWhenSensitiveWordHit() {
        ModerationGuard guard = guard(properties(true, "BLOCK", "BOTH"), List.of(word("badword", "ABUSE", 2)));

        ModerationRejectException exception = assertThrows(ModerationRejectException.class,
                () -> guard.checkRequest(requestContext(requestBody())));

        assertEquals("ABUSE", exception.categoryCode());
    }

    @Test
    void blocksWholeResponseWhenSensitiveWordHit() {
        ModerationGuard guard = guard(properties(true, "BLOCK", "BOTH"), List.of(word("badword", "ABUSE", 2)));

        ModerationRejectException exception = assertThrows(ModerationRejectException.class,
                () -> guard.checkResponse(responseContext(RESPONSE_BODY)));

        assertEquals("ABUSE", exception.categoryCode());
    }

    @Test
    void blocksResponseGivenAsMap() {
        ModerationGuard guard = guard(properties(true, "BLOCK", "BOTH"), List.of(word("badword", "ABUSE", 2)));
        // 插件拿到的是已注入 knot_extend 的 Map（非原始 JSON 字符串）
        Map<String, Object> body = Map.of("choices", List.of(Map.of("message",
                Map.of("role", "assistant", "content", "here is badword"))));

        assertThrows(ModerationRejectException.class, () -> guard.checkResponse(responseContext(body)));
    }

    @Test
    void streamingResponseBodyPassesThrough() {
        ModerationGuard guard = guard(properties(true, "BLOCK", "BOTH"), List.of(word("badword", "ABUSE", 2)));
        // 流式：响应体是 StreamingResponseBody，拿不到文本 → 不拦截（不截断流）
        Object streamBody = new Object();

        assertDoesNotThrow(() -> guard.checkResponse(responseContext(streamBody)));
    }

    @Test
    void flagActionPassesThrough() {
        ModerationGuard guard = guard(properties(true, "FLAG", "BOTH"), List.of(word("badword", "ABUSE", 2)));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(requestBody())));
    }

    @Test
    void skipsWhenDisabled() {
        ModerationGuard guard = guard(properties(false, "BLOCK", "BOTH"), List.of(word("badword", "ABUSE", 2)));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(requestBody())));
    }

    @Test
    void skipsWhenScopeExcludesDirection() {
        ModerationGuard guard = guard(properties(true, "BLOCK", "RESPONSE"), List.of(word("badword", "ABUSE", 2)));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(requestBody())));
    }

    @Test
    void failsOpenWhenWordListUnavailable() {
        ModerationProperties properties = properties(true, "BLOCK", "BOTH");
        ModerationGuard guard = new ModerationGuard(properties,
                new ModerationSnapshotCache(new ModerationWordSource() {
                    @Override
                    public List<ModerationTerm> loadTerms() {
                        throw new IllegalStateException("db down");
                    }

                    @Override
                    public long currentVersion() {
                        throw new IllegalStateException("db down");
                    }
                }, 0L),
                new ModerationEventSink());

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(requestBody())));
    }

    @Test
    void severityBelowThresholdDoesNotTriggerAction() {
        ModerationGuard guard = guard(properties(true, "BLOCK", "BOTH"), List.of(word("badword", "ABUSE", 0)));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(requestBody())));
    }

    @Test
    void emptyWordListPassesThrough() {
        ModerationGuard guard = guard(properties(true, "BLOCK", "BOTH"), List.of());

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(requestBody())));
    }
}
