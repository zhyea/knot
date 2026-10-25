package org.chobit.knot.gateway.adapter.support.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.adapter.protocol.StreamLineAdapter;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式双向转换契约（{@link AnthropicStreamSupport}）。
 *
 * <p>盯六件事：① {@code event:} 行抑制与空行保留（SSE 分隔符不能丢）；② 收尾事件合成
 * {@code [DONE]}；③ 首个事件合成骨架（{@code message_start} / Responses 三段头）；
 * ④ 工具块的流式增量（{@code tool_use} ↔ {@code tool_calls} / {@code function_call}）；
 * ⑤ 未知事件与非 JSON 原样透传；⑥ chunk id / 块序号全程一致（状态挂在 per-request 实例上）。</p>
 */
class AnthropicStreamSupportTest {

    // ============================ 方向一：Messages → chat.completion.chunk ============================

    @Test
    @DisplayName("Messages 流 → chat chunk：抑制 event 行，收尾事件合成 [DONE]")
    void convertsMessagesStreamToChatChunks() {
        StreamLineAdapter adapter = AnthropicStreamSupport.messagesToChatCompletionChunks(null);

        List<String> out = feed(adapter, messagesStream("end_turn"));

        assertFalse(out.stream().anyMatch(line -> line.startsWith("event:")),
                "OpenAI 客户端不认 event: 行，必须抑制，实际：" + out);
        assertTrue(out.contains(""), "空行是 SSE 事件分隔符，必须保留");

        List<Map<String, Object>> chunks = dataPayloads(out);
        assertEquals(3, chunks.size(), "首 chunk + 文本增量 + finish chunk，实际：" + out);

        Map<String, Object> first = firstChoice(chunks.get(0));
        assertEquals("assistant", asMap(first.get(AiPayloadFields.DELTA)).get(AiPayloadFields.ROLE));
        assertEquals("chat.completion.chunk", chunks.get(0).get("object"));
        assertEquals("chatcmpl-msg_1", chunks.get(0).get(AiPayloadFields.ID), "chunk id 由上游 message.id 派生");

        assertEquals("Hi", asMap(firstChoice(chunks.get(1)).get(AiPayloadFields.DELTA))
                .get(AiPayloadFields.CONTENT));

        assertEquals("stop", firstChoice(chunks.get(2)).get("finish_reason"));
        assertEquals("chatcmpl-msg_1", chunks.get(2).get(AiPayloadFields.ID), "全程 id 必须一致");

        assertEquals("data: [DONE]", out.stream().filter(line -> line.startsWith("data: [DONE]"))
                .findFirst().orElseThrow(), "message_stop 必须合成为 [DONE]");
    }

    @Test
    @DisplayName("Messages 流 → chat chunk：usage 按 OpenAI 口径带在 finish chunk 上")
    void carriesUsageInFinishChunk() {
        StreamLineAdapter adapter = AnthropicStreamSupport.messagesToChatCompletionChunks(null);

        List<Map<String, Object>> chunks = dataPayloads(feed(adapter, messagesStream("end_turn")));

        Map<String, Object> usage = asMap(chunks.get(chunks.size() - 1).get(AiPayloadFields.USAGE));
        assertEquals(10L, ((Number) usage.get(AiPayloadFields.PROMPT_TOKENS)).longValue());
        assertEquals(5L, ((Number) usage.get(AiPayloadFields.COMPLETION_TOKENS)).longValue());
        assertEquals(15L, ((Number) usage.get(AiPayloadFields.TOTAL_TOKENS)).longValue());
    }

    @Test
    @DisplayName("Messages 流 → chat chunk：tool_use 块与 input_json_delta 合成 tool_calls 增量")
    void convertsToolUseStreamToToolCallDeltas() {
        StreamLineAdapter adapter = AnthropicStreamSupport.messagesToChatCompletionChunks(null);

        List<Map<String, Object>> chunks = dataPayloads(feed(adapter,
                "event: message_start",
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude\","
                        + "\"usage\":{\"input_tokens\":10}}}",
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":"
                        + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"get_weather\",\"input\":{}}}",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":"
                        + "{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"city\\\":\"}}",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":"
                        + "{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"\\u676d\\u5dde\\\"}\"}}",
                "event: message_delta",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},"
                        + "\"usage\":{\"output_tokens\":5}}"));

        // chunks: [首 chunk(role), tool 起始, 参数片 1, 参数片 2, finish]
        assertEquals(5, chunks.size(), "实际：" + chunks);

        Map<String, Object> startDelta = asMap(firstChoice(chunks.get(1)).get(AiPayloadFields.DELTA));
        Map<String, Object> start = asMap(((List<?>) startDelta.get(AiPayloadFields.TOOL_CALLS)).get(0));
        assertEquals(0, ((Number) start.get(AiPayloadFields.INDEX)).intValue(), "OpenAI tool_calls 序号从 0 起");
        assertEquals("toolu_1", start.get(AiPayloadFields.ID));
        assertEquals("get_weather", asMap(start.get(AiPayloadFields.FUNCTION)).get(AiPayloadFields.NAME));

        Map<String, Object> pieceDelta = asMap(firstChoice(chunks.get(2)).get(AiPayloadFields.DELTA));
        Map<String, Object> piece1 = asMap(((List<?>) pieceDelta.get(AiPayloadFields.TOOL_CALLS)).get(0));
        assertEquals("{\"city\":", asMap(piece1.get(AiPayloadFields.FUNCTION)).get(AiPayloadFields.ARGUMENTS));
        assertEquals(0, ((Number) piece1.get(AiPayloadFields.INDEX)).intValue(), "参数片必须归属同一个 tool_call");

        assertEquals("tool_calls", firstChoice(chunks.get(4)).get("finish_reason"),
                "Anthropic 的 tool_use 对应 OpenAI 的 tool_calls");
    }

    @Test
    @DisplayName("ping 事件抑制；非 JSON 与 error 事件原样透传")
    void suppressesPingAndPassesUnknownThrough() {
        StreamLineAdapter adapter = AnthropicStreamSupport.messagesToChatCompletionChunks(null);

        assertEquals(List.of(), adapter.adapt("event: ping"));
        assertEquals(List.of(), adapter.adapt("data: {\"type\":\"ping\"}"));
        assertEquals(List.of("data: not-json"), adapter.adapt("data: not-json"));
        assertEquals(List.of("data: {\"type\":\"error\",\"error\":{\"message\":\"boom\"}}"),
                adapter.adapt("data: {\"type\":\"error\",\"error\":{\"message\":\"boom\"}}"),
                "错误事件不能被吞，也不能改写成成功形状");
    }

    // ============================ 方向二：Messages → text_completion ============================

    @Test
    @DisplayName("Messages 流 → text_completion chunk：只产文本与 finish")
    void convertsMessagesStreamToTextCompletionChunks() {
        StreamLineAdapter adapter = AnthropicStreamSupport.messagesToTextCompletionChunks(null);

        List<String> out = feed(adapter, messagesStream("end_turn"));
        List<Map<String, Object>> chunks = dataPayloads(out);

        assertEquals(2, chunks.size(), "文本增量 + finish（旧协议无 role 首块），实际：" + out);
        assertEquals("text_completion", chunks.get(0).get("object"));
        assertEquals("Hi", firstChoice(chunks.get(0)).get(AiPayloadFields.TEXT));
        assertEquals("stop", firstChoice(chunks.get(1)).get("finish_reason"));
        assertTrue(out.contains("data: [DONE]"));
    }

    // ============================ 方向三：Messages → Responses ============================

    @Test
    @DisplayName("Messages 流 → Responses 事件：合成 response.created / output_text.delta / completed")
    void convertsMessagesStreamToResponsesEvents() {
        StreamLineAdapter adapter = AnthropicStreamSupport.messagesToResponsesEvents(null);

        List<String> out = feed(adapter, messagesStream("end_turn"));

        assertTrue(out.contains("event: response.created"), "首个事件必须合成响应骨架，实际：" + out);
        assertTrue(out.contains("event: response.output_item.added"));
        assertTrue(out.contains("event: response.content_part.added"));
        assertTrue(out.contains("event: response.output_text.delta"));
        assertTrue(out.contains("event: response.output_text.done"));
        assertTrue(out.contains("event: response.completed"));

        Map<String, Object> completed = dataPayloads(out).stream()
                .filter(payload -> "response.completed".equals(payload.get(AiPayloadFields.TYPE)))
                .findFirst().orElseThrow();
        Map<String, Object> response = asMap(completed.get("response"));
        assertEquals("completed", response.get(AiPayloadFields.TYPE) == null
                ? response.get("status") : response.get("status"));
        Map<String, Object> usage = asMap(response.get(AiPayloadFields.USAGE));
        assertEquals(10L, ((Number) usage.get(AiPayloadFields.INPUT_TOKENS)).longValue());
        assertEquals(5L, ((Number) usage.get(AiPayloadFields.OUTPUT_TOKENS)).longValue());

        List<?> output = (List<?>) response.get("output");
        Map<String, Object> item = asMap(output.get(0));
        Map<String, Object> part = asMap(((List<?>) item.get(AiPayloadFields.CONTENT)).get(0));
        assertEquals("Hi", part.get(AiPayloadFields.TEXT), "completed 里的正文必须是完整累积文本");
    }

    // ============================ 方向四：chat chunk → Messages ============================

    @Test
    @DisplayName("chat chunk 流 → Messages：首 chunk 合成 message_start，[DONE] 补收尾三事件")
    void convertsChatChunksToMessagesEvents() {
        StreamLineAdapter adapter = AnthropicStreamSupport.chatChunksToMessages(null);

        List<String> out = feed(adapter,
                "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4\",\"choices\":[{\"index\":0,"
                        + "\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}",
                "",
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,"
                        + "\"delta\":{\"content\":\"Hi\"},\"finish_reason\":null}]}",
                "",
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,\"delta\":{},"
                        + "\"finish_reason\":\"stop\"}],\"usage\":{\"completion_tokens\":5}}",
                "",
                "data: [DONE]");

        assertTrue(out.contains("event: message_start"), "Anthropic 客户端要求先有消息骨架，实际：" + out);
        assertTrue(out.contains("event: content_block_start"));
        assertTrue(out.contains("event: content_block_delta"));
        assertTrue(out.contains("event: content_block_stop"));
        assertTrue(out.contains("event: message_delta"));
        assertTrue(out.contains("event: message_stop"));
        assertFalse(out.contains("data: [DONE]"), "Messages 协议没有 [DONE]，必须换成品类事件");

        Map<String, Object> messageStart = dataPayloads(out).stream()
                .filter(payload -> "message_start".equals(payload.get(AiPayloadFields.TYPE)))
                .findFirst().orElseThrow();
        Map<String, Object> message = asMap(messageStart.get("message"));
        assertEquals("msg_chatcmpl-1", message.get(AiPayloadFields.ID));
        assertEquals("assistant", message.get(AiPayloadFields.ROLE));

        Map<String, Object> textDelta = dataPayloads(out).stream()
                .filter(payload -> "content_block_delta".equals(payload.get(AiPayloadFields.TYPE)))
                .filter(payload -> asMap(payload.get(AiPayloadFields.DELTA)).get(AiPayloadFields.TYPE)
                        .equals("text_delta"))
                .findFirst().orElseThrow();
        assertEquals("Hi", asMap(textDelta.get(AiPayloadFields.DELTA)).get(AiPayloadFields.TEXT));

        Map<String, Object> messageDelta = dataPayloads(out).stream()
                .filter(payload -> "message_delta".equals(payload.get(AiPayloadFields.TYPE)))
                .findFirst().orElseThrow();
        assertEquals("end_turn", asMap(messageDelta.get(AiPayloadFields.DELTA)).get("stop_reason"),
                "OpenAI 的 stop 对应 Anthropic 的 end_turn");
    }

    @Test
    @DisplayName("chat chunk 流 → Messages：纯工具调用轮次不留空 text 块")
    void skipsEmptyTextBlockForPureToolRound() {
        StreamLineAdapter adapter = AnthropicStreamSupport.chatChunksToMessages(null);

        List<Map<String, Object>> payloads = dataPayloads(feed(adapter,
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,"
                        + "\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"get_weather\",\"arguments\":\"\"}}]},"
                        + "\"finish_reason\":null}]}",
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":"
                        + "[{\"index\":0,\"function\":{\"arguments\":\"{\\\"city\\\":\\\"杭州\\\"}\"}}]},"
                        + "\"finish_reason\":null}]}",
                "data: [DONE]"));

        List<Map<String, Object>> blockStarts = payloads.stream()
                .filter(payload -> "content_block_start".equals(payload.get(AiPayloadFields.TYPE)))
                .toList();
        assertEquals(1, blockStarts.size(), "只应有 tool_use 块，不应有空 text 块：" + payloads);
        Map<String, Object> block = asMap(blockStarts.get(0).get("content_block"));
        assertEquals("tool_use", block.get(AiPayloadFields.TYPE));
        assertEquals("call_1", block.get(AiPayloadFields.ID));
        assertEquals("get_weather", block.get(AiPayloadFields.NAME));
        assertEquals(0, ((Number) blockStarts.get(0).get(AiPayloadFields.INDEX)).intValue(),
                "无 text 块时 tool_use 占 index 0");

        Map<String, Object> argumentDelta = payloads.stream()
                .filter(payload -> "content_block_delta".equals(payload.get(AiPayloadFields.TYPE)))
                .findFirst().orElseThrow();
        assertEquals("input_json_delta", asMap(argumentDelta.get(AiPayloadFields.DELTA))
                .get(AiPayloadFields.TYPE));
        assertEquals("{\"city\":\"杭州\"}", asMap(argumentDelta.get(AiPayloadFields.DELTA)).get("partial_json"));
        assertEquals(0, ((Number) argumentDelta.get(AiPayloadFields.INDEX)).intValue(), "参数片归属同一个块");
    }

    // ============================ 辅助 ============================

    /** 一段标准的 Anthropic SSE 文本流（message_start → 文本增量 → 收尾）。 */
    private List<String> messagesStream(String stopReason) {
        return List.of(
                "event: message_start",
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-sonnet-4\","
                        + "\"usage\":{\"input_tokens\":10}}}",
                "",
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":"
                        + "{\"type\":\"text\",\"text\":\"\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}",
                "",
                "event: content_block_stop",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "",
                "event: message_delta",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stopReason + "\"},"
                        + "\"usage\":{\"output_tokens\":5}}",
                "",
                "event: message_stop",
                "data: {\"type\":\"message_stop\"}",
                "");
    }

    private List<String> feed(StreamLineAdapter adapter, String... lines) {
        return feed(adapter, List.of(lines));
    }

    private List<String> feed(StreamLineAdapter adapter, List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.addAll(adapter.adapt(line));
        }
        return out;
    }

    /** 从输出行里取出所有非空 data 载荷（已解析）。 */
    private List<Map<String, Object>> dataPayloads(List<String> lines) {
        return lines.stream()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .filter(payload -> !payload.isEmpty() && !"[DONE]".equals(payload))
                .map(this::readMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> firstChoice(Map<String, Object> chunk) {
        List<?> choices = (List<?>) chunk.get("choices");
        assertNotNull(choices, "chunk 必须带 choices：" + chunk);
        return asMap(choices.get(0));
    }

    private Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
