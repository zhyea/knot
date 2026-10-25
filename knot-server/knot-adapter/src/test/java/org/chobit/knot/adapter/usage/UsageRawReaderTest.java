package org.chobit.knot.adapter.usage;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.usage.UsageRawReader;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link UsageRawReader} 的 usage 定位：顶层 {@code usage} 优先，
 * Responses 协议事件（{@code response.completed} / {@code response.incomplete} 等）
 * 下钻 {@code response.usage} 包装。
 */
class UsageRawReaderTest {

    @Test
    void throwsOnMalformedJsonInsteadOfReturningNull() {
        assertThrows(JsonProcessingException.class, () -> UsageRawReader.readBody("{not-json-at-all"),
                "解析失败曾被引擎吞成 null，与「上游没回 usage」无法区分");
    }

    @Test
    void readsTopLevelUsageFirst() {
        Map<String, Object> body = Map.of(
                "usage", Map.of("input_tokens", 10, "output_tokens", 5),
                "response", Map.of("usage", Map.of("input_tokens", 99, "output_tokens", 99)));

        Map<String, Object> usage = UsageRawReader.readUsage(body);

        assertNotNull(usage);
        assertEquals(10, usage.get("input_tokens"), "顶层 usage 存在时不下钻 response 包装");
    }

    @Test
    void unwrapsResponsesEventUsage() {
        Map<String, Object> event = Map.of(
                "type", "response.incomplete",
                "sequence_number", 123,
                "response", Map.of(
                        "id", "resp_x",
                        "status", "incomplete",
                        "usage", Map.of("input_tokens", 65, "output_tokens", 256, "total_tokens", 321)));

        Map<String, Object> usage = UsageRawReader.readUsage(event);

        assertNotNull(usage, "Responses 事件的 response.usage 必须被识别");
        assertEquals(65, usage.get("input_tokens"));
        assertEquals(321, usage.get("total_tokens"));
    }

    @Test
    void returnsNullWhenNoUsageAnywhere() {
        assertNull(UsageRawReader.readUsage(Map.of("type", "response.output_text.delta", "delta", "你")));
        assertNull(UsageRawReader.readUsage(Map.of("response", Map.of("id", "resp_x"))));
        assertNull(UsageRawReader.readUsage(null));
    }

    @Test
    void scoresResponsesEventAsBestBody() throws Exception {
        String sse = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"你\"}\n"
                + "\n"
                + "data: {\"type\":\"response.incomplete\",\"response\":{\"usage\":{\"input_tokens\":65,\"output_tokens\":256,\"total_tokens\":321}}}\n"
                + "\n"
                + "data: [DONE]\n";

        Map<String, Object> body = UsageRawReader.readBody(sse);

        assertNotNull(body);
        assertEquals("response.incomplete", body.get("type"),
                "带 response.usage 的事件应凭 token 量被选为信息量最大的事件");
        assertNotNull(UsageRawReader.readUsage(body));
    }

    @Test
    void extractsTokenBillingFromResponsesWrapper() {
        Map<String, Object> event = Map.of(
                "type", "response.incomplete",
                "response", Map.of(
                        "usage", Map.of(
                                "input_tokens", 65,
                                "input_tokens_details", Map.of("cached_tokens", 0),
                                "output_tokens", 256,
                                "output_tokens_details", Map.of("reasoning_tokens", 256),
                                "total_tokens", 321)));

        TokenBillingUsage usage = TokenBillingUsage.fromResponse(event);

        assertEquals(65, usage.inputTokens());
        assertEquals(256, usage.outputTokens());
        assertEquals(321, usage.totalTokens());
    }
}
