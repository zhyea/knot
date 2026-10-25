package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoutingRuleDebugSupport} 单测：钉住调试面板与真实调试请求之间必须同源的几处口径。
 *
 * <p>核心不变量是 {@link RoutingRuleDebugSupport#promptFieldOf} 与
 * {@link RoutingRuleDebugSupport#buildRequestBody} 必须一致——面板告诉用户「prompt 填在这个字段」，
 * 而实际请求体也必须把 prompt 放在同一个字段，否则用户改了输入框却发出一个没有 prompt 的请求。</p>
 */
class RoutingRuleDebugSupportTest {

    private static final String PROMPT = "PROMPT-X";

    @Test
    @DisplayName("prompt 字段路径与请求体补全口径一致（逐协议）")
    void promptFieldMatchesRequestBuilder() {
        for (ModelApiProtocolEnum protocol : RoutingRuleDebugSupport.DEBUGGABLE_PROTOCOLS) {
            Map<String, Object> body = RoutingRuleDebugSupport.buildRequestBody(protocol, PROMPT, null);
            String field = RoutingRuleDebugSupport.promptFieldOf(protocol);
            if (field == null) {
                assertFalse(body.containsValue(PROMPT),
                    protocol + " 声明无 prompt 语义，但请求体里写了 prompt：" + body);
                continue;
            }
            assertEquals(PROMPT, readPath(body, field),
                protocol + " 的 prompt 字段路径与实际请求体不一致：" + body);
        }
    }

    @Test
    @DisplayName("所有可调试协议都有网关路径，CUSTOM 明确拒绝")
    void gatewayPathCoversEveryDebuggableProtocol() {
        for (ModelApiProtocolEnum protocol : RoutingRuleDebugSupport.DEBUGGABLE_PROTOCOLS) {
            String path = RoutingRuleDebugSupport.buildGatewayTestPath(protocol);
            assertTrue(path.startsWith("/"), protocol + " 的网关路径异常：" + path);
        }
        // 白名单与 switch 分支必须互相对齐：漏写分支会在面板下发能力时 500
        Set<ModelApiProtocolEnum> pathCovered = Set.of(ModelApiProtocolEnum.values()).stream()
            .filter(protocol -> pathOrNull(protocol) != null)
            .collect(Collectors.toSet());
        assertEquals(RoutingRuleDebugSupport.DEBUGGABLE_PROTOCOLS, pathCovered,
            "可调试白名单必须与已实现网关路径的协议集合完全一致");
    }

    @Test
    @DisplayName("调试能力下发只覆盖可调试协议")
    void hintCoversEveryDebuggableProtocol() {
        for (ModelApiProtocolEnum protocol : RoutingRuleDebugSupport.DEBUGGABLE_PROTOCOLS) {
            assertTrue(RoutingRuleDebugSupport.hintOf(protocol) != null
                    && !RoutingRuleDebugSupport.hintOf(protocol).isBlank(),
                protocol + " 缺少调试说明文案");
        }
        assertNull(RoutingRuleDebugSupport.hintOf(ModelApiProtocolEnum.CUSTOM), "CUSTOM 不应有调试文案");
    }

    @Test
    @DisplayName("请求体剔除 model 并保留调用方提交的其它字段")
    void buildRequestBodyDropsModelAndKeepsSubmittedFields() {
        Map<String, Object> body = RoutingRuleDebugSupport.buildRequestBody(
            ModelApiProtocolEnum.CHAT_COMPLETIONS, PROMPT,
            Map.of("model", "gpt-4o", "temperature", 0.2d, "stream", true));

        assertFalse(body.containsKey("model"), "model 必须剔除：网关按路由目标覆盖，客户端传了会打架");
        assertEquals(Double.valueOf(0.2d), body.get("temperature"));
        assertEquals(Boolean.TRUE, body.get("stream"), "stream 必须保留，否则流式调试退化成同步");
        assertEquals(PROMPT, readPath(body, "messages[0].content"));
    }

    @Test
    @DisplayName("调用方已给 messages 时不覆盖")
    void buildRequestBodyKeepsSubmittedMessages() {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "HI"));
        Map<String, Object> body = RoutingRuleDebugSupport.buildRequestBody(
            ModelApiProtocolEnum.MESSAGES, PROMPT, Map.of("messages", messages));

        assertEquals(messages, body.get("messages"));
    }

    @Test
    @DisplayName("空白 prompt 回落默认测试文案")
    void normalizeTestPromptFallsBackToDefault() {
        assertEquals(RoutingRuleDebugSupport.DEFAULT_TEST_PROMPT,
            RoutingRuleDebugSupport.normalizeTestPrompt("   "));
        assertEquals(RoutingRuleDebugSupport.DEFAULT_TEST_PROMPT,
            RoutingRuleDebugSupport.normalizeTestPrompt(null));
        assertEquals("hi", RoutingRuleDebugSupport.normalizeTestPrompt("  hi  "));
    }

    @Test
    @DisplayName("JSON curl 转义单引号，保证可直接粘进 shell")
    void jsonCurlEscapesSingleQuote() {
        String curl = RoutingRuleDebugSupport.buildTestCurl(
            "http://gw:8080", "/openai/v1/chat/completions", "sk-x", "rule-a",
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            Map.of("messages", List.of(Map.of("role", "user", "content", "it's"))));

        assertTrue(curl.contains("-H 'Rule: rule-a'"), curl);
        assertTrue(curl.contains("-H 'traceparent: " + RoutingRuleDebugSupport.TRACEPARENT + "'"), curl);
        assertTrue(curl.contains("it'\\''s"), "单引号必须转义为 '\\''：" + curl);
    }

    @Test
    @DisplayName("图像编辑 curl 走 multipart：存在的本地文件用 @ 引用")
    void multipartCurlUsesFileReferenceForExistingPath(@TempDir Path dir) throws Exception {
        Path image = dir.resolve("in.png");
        Files.writeString(image, "x");

        String curl = RoutingRuleDebugSupport.buildTestCurl(
            "http://gw:8080", "/openai/v1/images/edits", "sk-x", "rule-a",
            ModelApiProtocolEnum.IMAGE_EDITS,
            Map.of("prompt", PROMPT, "image", image.toString()));

        assertTrue(curl.contains("-F 'image=@" + image.toString().replace("\\", "/") + "'"),
            "存在的文件必须用 @ 引用：" + curl);
        assertTrue(curl.contains("-F 'prompt=" + PROMPT + "'"), curl);
        assertFalse(curl.contains("Content-Type: application/json"), "multipart 不应带 JSON 头：" + curl);
    }

    @Test
    @DisplayName("图像字段指向不存在的路径：拒绝而非静默发空文件")
    void multipartRejectsMissingFile() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> RoutingRuleDebugSupport.toMultipartBody(Map.of("image", "/no/such/file.png")));

        assertTrue(ex.getMessage().contains("image"), ex.getMessage());
    }

    /**
     * 按 {@code promptFieldOf} 声明的路径取值：只支持本类实际产出的两种形态，
     * 出现新形态时这里必须同步，否则测试会以「路径读不到」失败而非静默通过。
     */
    private static Object readPath(Map<String, Object> body, String field) {
        if ("messages[0].content".equals(field)) {
            Object messages = body.get("messages");
            assertTrue(messages instanceof List<?>, "messages 应为列表：" + body);
            Object first = ((List<?>) messages).get(0);
            assertTrue(first instanceof Map<?, ?>, "messages[0] 应为对象：" + body);
            return ((Map<?, ?>) first).get("content");
        }
        return body.get(field);
    }

    private static String pathOrNull(ModelApiProtocolEnum protocol) {
        try {
            return RoutingRuleDebugSupport.buildGatewayTestPath(protocol);
        } catch (BusinessException ex) {
            return null;
        }
    }
}
