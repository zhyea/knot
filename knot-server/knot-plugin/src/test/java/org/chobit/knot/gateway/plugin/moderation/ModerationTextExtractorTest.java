package org.chobit.knot.gateway.plugin.moderation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationTextExtractorTest {

    @Test
    void extractsMessageContent() {
        Map<String, Object> body = Map.of("messages", List.of(Map.of("role", "user", "content", "hello")));

        assertEquals(List.of("hello"), ModerationTextExtractor.fromRequest(body));
    }

    @Test
    void extractsPromptField() {
        Map<String, Object> body = Map.of("prompt", "hi there");

        assertEquals(List.of("hi there"), ModerationTextExtractor.fromRequest(body));
    }

    @Test
    void extractsTextPartsFromMultimodalContent() {
        Map<String, Object> body = Map.of("messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "text", "text", "p"),
                Map.of("type", "text", "text", "a"),
                Map.of("type", "text", "text", "b"),
                Map.of("type", "text", "text", "i"),
                Map.of("type", "text", "text", "s")))));

        assertEquals(List.of("p", "a", "b", "i", "s"), ModerationTextExtractor.fromRequest(body));
    }

    @Test
    void extractsContentFromResponseJson() {
        String response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"bad\"}}]}";

        assertEquals(List.of("bad"), ModerationTextExtractor.fromResponse(response));
    }

    @Test
    void ignoresNonTextFieldsInResponse() {
        String response = "{\"model\":\"gpt-4\",\"id\":\"x\",\"choices\":[{\"finish_reason\":\"stop\"}]}";

        assertTrue(ModerationTextExtractor.fromResponse(response).isEmpty());
    }

    @Test
    void returnsEmptyForBlankOrNullResponse() {
        assertTrue(ModerationTextExtractor.fromResponse(null).isEmpty());
        assertTrue(ModerationTextExtractor.fromResponse("  ").isEmpty());
        assertTrue(ModerationTextExtractor.fromResponseObject(null).isEmpty());
    }
}
