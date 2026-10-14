package org.chobit.knot.gateway.plugin.moderation;

import org.chobit.knot.gateway.constants.enums.ModerationActionEnum;
import org.chobit.knot.gateway.constants.enums.ModerationScopeEnum;
import org.chobit.knot.gateway.moderation.ModerationTerm;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.plugin.components.ModerationWordCache;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.util.TextNormalizer;
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

    private static PluginProperties properties(ModerationActionEnum action, ModerationScopeEnum scope) {
        PluginProperties properties = new PluginProperties();
        properties.getModeration().setAction(action.code());
        properties.getModeration().setScope(scope.code());
        return properties;
    }

    private static ModerationGuard guard(PluginProperties properties) {
        return new ModerationGuard(new ModerationEventSink(), new ModerationWordCache(properties), properties);
    }

    private static GatewayPluginContext requestContext(List<ModerationTerm> words,
                                                      Map<String, Object> requestBody) {
        return context(PluginStageCode.GATEWAY_REQUEST, words, requestBody, null);
    }

    private static GatewayPluginContext responseContext(List<ModerationTerm> words,
                                                        Object responseBody) {
        return context(PluginStageCode.GATEWAY_RESPONSE, words, Map.of(), responseBody);
    }

    private static GatewayPluginContext context(PluginStageCode stage,
                                                List<ModerationTerm> words,
                                                Map<String, Object> requestBody,
                                                Object responseBody) {
        return new GatewayPluginContext("trace-1", null, stage, "key-1", "rule-a",
                "openai", null, words, requestBody, null, responseBody, null);
    }

    private static Map<String, Object> requestBody() {
        return Map.of("messages", List.of(Map.of("role", "user", "content", "say badword")));
    }

    @Test
    void blocksRequestWhenSensitiveWordHit() {
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 2));

        ModerationRejectException exception = assertThrows(ModerationRejectException.class,
                () -> guard.checkRequest(requestContext(words, requestBody())));

        assertEquals("ABUSE", exception.categoryCode());
    }

    @Test
    void blocksWholeResponseWhenSensitiveWordHit() {
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 2));

        ModerationRejectException exception = assertThrows(ModerationRejectException.class,
                () -> guard.checkResponse(responseContext(words, RESPONSE_BODY)));

        assertEquals("ABUSE", exception.categoryCode());
    }

    @Test
    void blocksResponseGivenAsMap() {
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 2));
        // 插件拿到的是已注入 knot_extend 的 Map（非原始 JSON 字符串）
        Map<String, Object> body = Map.of("choices", List.of(Map.of("message",
                Map.of("role", "assistant", "content", "here is badword"))));

        assertThrows(ModerationRejectException.class,
                () -> guard.checkResponse(responseContext(words, body)));
    }

    @Test
    void streamingResponseBodyPassesThrough() {
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 2));
        // 流式：响应体是 StreamingResponseBody，拿不到文本 → 不拦截（不截断流）
        Object streamBody = new Object();

        assertDoesNotThrow(() -> guard.checkResponse(responseContext(words, streamBody)));
    }

    @Test
    void flagActionPassesThrough() {
        ModerationGuard guard = guard(properties(ModerationActionEnum.FLAG, ModerationScopeEnum.BOTH));
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 2));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(words, requestBody())));
    }

    @Test
    void defaultsToFlagWhenConfigAbsent() {
        // 配置缺失（未显式设置）：回退默认 FLAG 影子模式，不阻断
        ModerationGuard guard = guard(new PluginProperties());
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 2));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(words, requestBody())));
    }

    @Test
    void skipsWhenScopeExcludesDirection() {
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.RESPONSE));
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 2));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(words, requestBody())));
    }

    @Test
    void passesThroughWhenWordsNull() {
        // 词库不可用（fail-open）：网关兜底空词表，守卫见空即放行
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(null, requestBody())));
    }

    @Test
    void severityBelowThresholdDoesNotTriggerAction() {
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 0));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(words, requestBody())));
    }

    @Test
    void emptyWordListPassesThrough() {
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));

        assertDoesNotThrow(() -> guard.checkRequest(requestContext(List.of(), requestBody())));
    }

    @Test
    void blocksOnEveryRequestWhenWordListInstanceReused() {
        // 网关每请求下发词表：编译缓存按内容版本复用，不能把后续请求漏掉
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));
        List<ModerationTerm> words = List.of(word("badword", "ABUSE", 2));

        assertThrows(ModerationRejectException.class,
                () -> guard.checkRequest(requestContext(words, requestBody())));
        assertThrows(ModerationRejectException.class,
                () -> guard.checkRequest(requestContext(words, requestBody())));
    }

    @Test
    void blocksOnEveryRequestWhenFreshWordListInstancePerRequest() {
        // 网关每请求下发新列表实例（内容相同）：内容版本命中必须复用编译结果
        ModerationGuard guard = guard(properties(ModerationActionEnum.BLOCK, ModerationScopeEnum.BOTH));

        assertThrows(ModerationRejectException.class,
                () -> guard.checkRequest(requestContext(List.of(word("badword", "ABUSE", 2)), requestBody())));
        assertThrows(ModerationRejectException.class,
                () -> guard.checkRequest(requestContext(List.of(word("badword", "ABUSE", 2)), requestBody())));
    }
}
