package org.chobit.knot.gateway.adapter.support.anthropic;

import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具语义双向转换契约（{@link AnthropicToolSupport}）。
 *
 * <p>盯五件事：① 两套 tools 形状（chat 的 {@code function} 包装 / Responses 的扁平）；
 * ② {@code tool_choice} 四档映射；③ 消息载体换向（{@code tool_calls} ↔ {@code tool_use}、
 * {@code role:tool} ↔ {@code tool_result}）；④ {@code arguments}(JSON 文本) ↔ {@code input}(对象)
 * 的编解码与失败退化；⑤ 形状不认识时原样保留，不猜不改。</p>
 */
class AnthropicToolSupportTest {

    private static final String SCHEMA = "parameters";

    @Test
    @DisplayName("chat 形状 tools（function 包装）转 Anthropic：parameters → input_schema")
    void convertsChatToolsToAnthropic() {
        Object converted = AnthropicToolSupport.toolsToAnthropic(List.of(
                Map.of(AiPayloadFields.TYPE, AiPayloadFields.FUNCTION,
                        AiPayloadFields.FUNCTION, Map.of(
                                AiPayloadFields.NAME, "get_weather",
                                AiPayloadFields.DESCRIPTION, "查天气",
                                SCHEMA, Map.of("type", "object")))));

        List<?> tools = assertInstanceOf(List.class, converted);
        Map<String, Object> tool = asMap(tools.get(0));
        assertEquals("get_weather", tool.get(AiPayloadFields.NAME));
        assertEquals("查天气", tool.get(AiPayloadFields.DESCRIPTION));
        assertEquals(Map.of("type", "object"), tool.get(AiPayloadFields.INPUT_SCHEMA));
        assertFalse(tool.containsKey(AiPayloadFields.FUNCTION), "Anthropic 没有 function 外层包装");
    }

    @Test
    @DisplayName("Responses 扁平形状 tools 同样能转（无 function 包装）")
    void convertsFlatToolsToAnthropic() {
        Object converted = AnthropicToolSupport.toolsToAnthropic(List.of(Map.of(
                AiPayloadFields.TYPE, AiPayloadFields.FUNCTION,
                AiPayloadFields.NAME, "get_weather",
                SCHEMA, Map.of("type", "object"))));

        Map<String, Object> tool = asMap(((List<?>) converted).get(0));
        assertEquals("get_weather", tool.get(AiPayloadFields.NAME));
        assertEquals(Map.of("type", "object"), tool.get(AiPayloadFields.INPUT_SCHEMA));
    }

    @Test
    @DisplayName("已是 Anthropic 形状的 tools 幂等（input_schema 不被丢掉）")
    void keepsAnthropicToolsIdempotent() {
        Object converted = AnthropicToolSupport.toolsToAnthropic(List.of(Map.of(
                AiPayloadFields.NAME, "get_weather",
                AiPayloadFields.INPUT_SCHEMA, Map.of("type", "object"))));

        assertEquals(Map.of("type", "object"),
                asMap(((List<?>) converted).get(0)).get(AiPayloadFields.INPUT_SCHEMA));
    }

    @Test
    @DisplayName("读不到工具名的形状不认识，返回 null 让调用方别动")
    void rejectsUnrecognizableTools() {
        assertNull(AnthropicToolSupport.toolsToAnthropic("not-a-list"));
        assertNull(AnthropicToolSupport.toolsToAnthropic(List.of(Map.of("weird", "shape"))));
    }

    @Test
    @DisplayName("tool_choice 四档映射：auto/none/required/具名")
    void mapsToolChoiceToAnthropic() {
        assertEquals(Map.of(AiPayloadFields.TYPE, "auto"), AnthropicToolSupport.toolChoiceToAnthropic("auto"));
        assertEquals(Map.of(AiPayloadFields.TYPE, "none"), AnthropicToolSupport.toolChoiceToAnthropic("none"));
        assertEquals(Map.of(AiPayloadFields.TYPE, "any"), AnthropicToolSupport.toolChoiceToAnthropic("required"),
                "OpenAI 的 required 对应 Anthropic 的 any");

        Object named = AnthropicToolSupport.toolChoiceToAnthropic(Map.of(
                AiPayloadFields.TYPE, AiPayloadFields.FUNCTION,
                AiPayloadFields.FUNCTION, Map.of(AiPayloadFields.NAME, "get_weather")));
        assertEquals(Map.of(AiPayloadFields.TYPE, "tool", AiPayloadFields.NAME, "get_weather"), named);
    }

    @Test
    @DisplayName("tool_choice 已是 Anthropic 形状时返回 null（幂等，不重复包装）")
    void keepsAnthropicToolChoiceUntouched() {
        assertNull(AnthropicToolSupport.toolChoiceToAnthropic(Map.of(AiPayloadFields.TYPE, "any")));
        assertNull(AnthropicToolSupport.toolChoiceToAnthropic(Map.of(AiPayloadFields.TYPE, "auto")));
    }

    @Test
    @DisplayName("assistant.tool_calls 折成 tool_use 块（文本块在前）")
    void convertsToolCallsToToolUseBlocks() {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put(AiPayloadFields.ROLE, "assistant");
        assistant.put(AiPayloadFields.CONTENT, "我查一下");
        assistant.put(AiPayloadFields.TOOL_CALLS, List.of(Map.of(
                AiPayloadFields.ID, "call_1",
                AiPayloadFields.TYPE, AiPayloadFields.FUNCTION,
                AiPayloadFields.FUNCTION, Map.of(
                        AiPayloadFields.NAME, "get_weather",
                        AiPayloadFields.ARGUMENTS, "{\"city\":\"杭州\"}"))));

        List<Map<String, Object>> converted = AnthropicToolSupport.messagesToAnthropic(List.of(assistant));

        assertEquals(1, converted.size());
        Map<String, Object> message = converted.get(0);
        assertEquals("assistant", message.get(AiPayloadFields.ROLE));
        assertFalse(message.containsKey(AiPayloadFields.TOOL_CALLS), "OpenAI 载体字段必须移除");

        List<?> blocks = (List<?>) message.get(AiPayloadFields.CONTENT);
        assertEquals(2, blocks.size(), "文本块 + 工具块");
        assertEquals(AiPayloadFields.TEXT, asMap(blocks.get(0)).get(AiPayloadFields.TYPE));
        Map<String, Object> toolUse = asMap(blocks.get(1));
        assertEquals(AiPayloadFields.TOOL_USE, toolUse.get(AiPayloadFields.TYPE));
        assertEquals("call_1", toolUse.get(AiPayloadFields.ID));
        assertEquals("get_weather", toolUse.get(AiPayloadFields.NAME));
        assertEquals(Map.of("city", "杭州"), toolUse.get(AiPayloadFields.INPUT), "arguments 必须反解析成对象");
    }

    @Test
    @DisplayName("连续的 role:tool 结果合并进同一条 user 消息")
    void mergesConsecutiveToolResults() {
        List<Map<String, Object>> converted = AnthropicToolSupport.messagesToAnthropic(List.of(
                Map.of(AiPayloadFields.ROLE, AiPayloadFields.TOOL,
                        AiPayloadFields.TOOL_CALL_ID, "call_1", AiPayloadFields.CONTENT, "晴，26 度"),
                Map.of(AiPayloadFields.ROLE, AiPayloadFields.TOOL,
                        AiPayloadFields.TOOL_CALL_ID, "call_2", AiPayloadFields.CONTENT, "阴，20 度")));

        assertEquals(1, converted.size(), "两条 tool 结果必须合成一条 user 消息");
        Map<String, Object> user = converted.get(0);
        assertEquals(AiPayloadFields.USER, user.get(AiPayloadFields.ROLE));
        List<?> blocks = (List<?>) user.get(AiPayloadFields.CONTENT);
        assertEquals(2, blocks.size());
        assertEquals(AiPayloadFields.TOOL_RESULT, asMap(blocks.get(0)).get(AiPayloadFields.TYPE));
        assertEquals("call_1", asMap(blocks.get(0)).get(AiPayloadFields.TOOL_USE_ID));
        assertEquals("晴，26 度", asMap(blocks.get(0)).get(AiPayloadFields.CONTENT));
    }

    @Test
    @DisplayName("反向：Anthropic tools / tool_choice 转 OpenAI chat 形状")
    void convertsAnthropicToolsToOpenAi() {
        Object tools = AnthropicToolSupport.toolsToOpenAi(List.of(Map.of(
                AiPayloadFields.NAME, "get_weather",
                AiPayloadFields.DESCRIPTION, "查天气",
                AiPayloadFields.INPUT_SCHEMA, Map.of("type", "object"))));

        Map<String, Object> wrapper = asMap(((List<?>) tools).get(0));
        assertEquals(AiPayloadFields.FUNCTION, wrapper.get(AiPayloadFields.TYPE));
        Map<String, Object> function = asMap(wrapper.get(AiPayloadFields.FUNCTION));
        assertEquals("get_weather", function.get(AiPayloadFields.NAME));
        assertEquals(Map.of("type", "object"), function.get(SCHEMA), "input_schema → parameters");

        assertEquals("required", AnthropicToolSupport.toolChoiceToOpenAi(Map.of(AiPayloadFields.TYPE, "any")));
        assertEquals("auto", AnthropicToolSupport.toolChoiceToOpenAi(Map.of(AiPayloadFields.TYPE, "auto")));
        Map<String, Object> named = asMap(AnthropicToolSupport.toolChoiceToOpenAi(Map.of(
                AiPayloadFields.TYPE, "tool", AiPayloadFields.NAME, "get_weather")));
        assertEquals(AiPayloadFields.FUNCTION, named.get(AiPayloadFields.TYPE));
        assertEquals("get_weather", asMap(named.get(AiPayloadFields.FUNCTION)).get(AiPayloadFields.NAME));
    }

    @Test
    @DisplayName("反向：tool_use 块折成 tool_calls、tool_result 拆成 role:tool 消息")
    void convertsAnthropicMessagesToOpenAi() {
        List<Map<String, Object>> converted = AnthropicToolSupport.messagesToOpenAi(List.of(
                Map.of(AiPayloadFields.ROLE, "assistant", AiPayloadFields.CONTENT, List.of(
                        Map.of(AiPayloadFields.TYPE, AiPayloadFields.TEXT, AiPayloadFields.TEXT, "查一下"),
                        Map.of(AiPayloadFields.TYPE, AiPayloadFields.TOOL_USE,
                                AiPayloadFields.ID, "toolu_1",
                                AiPayloadFields.NAME, "get_weather",
                                AiPayloadFields.INPUT, Map.of("city", "杭州")))),
                Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER, AiPayloadFields.CONTENT, List.of(
                        Map.of(AiPayloadFields.TYPE, AiPayloadFields.TOOL_RESULT,
                                AiPayloadFields.TOOL_USE_ID, "toolu_1",
                                AiPayloadFields.CONTENT, "晴，26 度")))));

        assertEquals(2, converted.size());
        Map<String, Object> assistant = converted.get(0);
        assertEquals("查一下", assistant.get(AiPayloadFields.CONTENT), "只取 text 块");
        Map<String, Object> call = asMap(((List<?>) assistant.get(AiPayloadFields.TOOL_CALLS)).get(0));
        assertEquals("toolu_1", call.get(AiPayloadFields.ID));
        assertEquals("{\"city\":\"杭州\"}", asMap(call.get(AiPayloadFields.FUNCTION)).get(AiPayloadFields.ARGUMENTS));

        Map<String, Object> toolMessage = converted.get(1);
        assertEquals(AiPayloadFields.TOOL, toolMessage.get(AiPayloadFields.ROLE));
        assertEquals("toolu_1", toolMessage.get(AiPayloadFields.TOOL_CALL_ID));
    }

    @Test
    @DisplayName("未知 content 块类型时整条消息原样保留（不丢内容）")
    void keepsMessagesWithUnknownBlocksUntouched() {
        Map<String, Object> withImage = Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER,
                AiPayloadFields.CONTENT, List.of(Map.of(AiPayloadFields.TYPE, "image", "source", Map.of())));

        List<Map<String, Object>> converted = AnthropicToolSupport.messagesToOpenAi(List.of(withImage));

        assertEquals(1, converted.size());
        assertEquals(List.of(Map.of(AiPayloadFields.TYPE, "image", "source", Map.of())),
                converted.get(0).get(AiPayloadFields.CONTENT), "半懂的转换比不改更危险");
    }

    @Test
    @DisplayName("响应侧块级转换：tool_use ↔ tool_calls / function_call")
    void convertsResponseBlocks() {
        List<?> content = List.of(
                Map.of(AiPayloadFields.TYPE, AiPayloadFields.TEXT, AiPayloadFields.TEXT, "查一下"),
                Map.of(AiPayloadFields.TYPE, AiPayloadFields.TOOL_USE,
                        AiPayloadFields.ID, "toolu_1",
                        AiPayloadFields.NAME, "get_weather",
                        AiPayloadFields.INPUT, Map.of("city", "杭州")));

        assertTrue(AnthropicToolSupport.hasToolUse(content));

        Map<String, Object> call = asMap(AnthropicToolSupport.toolCallsOf(content).get(0));
        assertEquals("toolu_1", call.get(AiPayloadFields.ID));
        assertEquals(AiPayloadFields.FUNCTION, call.get(AiPayloadFields.TYPE));
        Map<String, Object> function = asMap(call.get(AiPayloadFields.FUNCTION));
        assertEquals("get_weather", function.get(AiPayloadFields.NAME));
        assertEquals("{\"city\":\"杭州\"}", function.get(AiPayloadFields.ARGUMENTS));

        Map<String, Object> item = asMap(AnthropicToolSupport.functionCallsOf(content).get(0));
        assertEquals(AiPayloadFields.FUNCTION_CALL, item.get(AiPayloadFields.TYPE));
        assertEquals("{\"city\":\"杭州\"}", item.get(AiPayloadFields.ARGUMENTS));

        assertTrue(AnthropicToolSupport.toolCallsOf(List.of(
                Map.of(AiPayloadFields.TYPE, AiPayloadFields.TEXT, AiPayloadFields.TEXT, "纯文本"))).isEmpty());
    }

    @Test
    @DisplayName("arguments 解析失败退化为空对象（Anthropic 要求 input 必须是对象）")
    void degradesBrokenArgumentsToEmptyObject() {
        List<Map<String, Object>> blocks = AnthropicToolSupport.toolUseBlocksOf(List.of(Map.of(
                AiPayloadFields.ID, "call_1",
                AiPayloadFields.FUNCTION, Map.of(
                        AiPayloadFields.NAME, "get_weather",
                        AiPayloadFields.ARGUMENTS, "{not-json"))));

        assertEquals(Map.of(), blocks.get(0).get(AiPayloadFields.INPUT), "解析不了也要给对象，否则上游 400");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
