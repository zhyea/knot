package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件日志正文出口的契约：默认模式下<b>结构保留、文本截断</b>。
 *
 * <p>盯三件事：① 短正文原样（字段仍可寻址）；② 长文本被截断并打标记；
 * ③ null 透传。模式由系统属性 {@code knot.plugin.log.body} 在类加载时解析一次，
 * 故本测试只覆盖默认的 {@code truncated} 分支。</p>
 */
class PluginLogSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void nullBodyStaysNull() {
        assertNull(PluginLogSupport.body(null));
    }

    @Test
    void shortBodyKeepsStructureAndValues() throws Exception {
        JsonNode node = MAPPER.readTree("{\"model\":\"gpt-4o\",\"vendor_only_field\":\"keep-me\"}");

        JsonNode body = PluginLogSupport.body(node);
        assertEquals("gpt-4o", body.get("model").asText(), "短正文原样：字段名与值都不能被脱敏改掉");
        assertEquals("keep-me", body.get("vendor_only_field").asText(), "未知字段必须原样保留");
    }

    @Test
    void longTextValueIsTruncatedAndMarked() throws Exception {
        StringBuilder prompt = new StringBuilder();
        prompt.append("x".repeat(2000));
        JsonNode node = MAPPER.readTree("{\"prompt\":\"" + prompt + "\"}");

        JsonNode body = PluginLogSupport.body(node);
        String value = body.get("prompt").asText();
        assertTrue(value.contains("truncated"), "截断必须显式标注，否则读日志的人会以为看到的是全文");
        assertTrue(value.length() < 600, "默认模式下正文不得整篇落盘，实际长度=" + value.length());
    }

    @Test
    void truncationSharesOneBudgetAcrossFields() throws Exception {
        JsonNode node = MAPPER.readTree("{\"a\":\"" + "x".repeat(400) + "\",\"b\":\"" + "y".repeat(400) + "\"}");

        JsonNode body = PluginLogSupport.body(node);
        assertTrue(body.get("b").asText().contains("truncated"),
                "预算是整份正文共享的：先到先得，后面的字段不再占用配额");
    }

    @Test
    void nonTextNodesArePreserved() throws Exception {
        JsonNode node = MAPPER.readTree("{\"n\":42,\"b\":true,\"arr\":[1,2]}");

        JsonNode body = PluginLogSupport.body(node);
        assertEquals(42, body.get("n").asInt());
        assertTrue(body.get("b").asBoolean());
        assertEquals(2, body.get("arr").size(), "结构性节点不参与截断，保持可寻址");
    }
}
