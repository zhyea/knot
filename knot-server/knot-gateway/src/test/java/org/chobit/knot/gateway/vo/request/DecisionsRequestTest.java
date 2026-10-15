package org.chobit.knot.gateway.vo.request;

import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decisions 协议请求体的绑定契约（OpenRouter AlphaDecisions {@code POST /api/alpha/decisions}）。
 *
 * <p>该协议不走 messages，入参是「待评估内容 + 一组待判定问题」，因此这里钉死三件事：
 * 四类 question 变体（多形态、开放键 criteria）必须原样承载不丢字段；
 * 上游蛇形字段 {@code session_id} 与驼峰字段的映射；未声明字段经基类 {@code extra} 透传。</p>
 */
class DecisionsRequestTest {

    private static final String PAYLOAD = """
            {
              "model": "jev",
              "questions": {
                "is_bug": {
                  "criteria": {
                    "false": "The customer is asking a question or requesting a feature.",
                    "true": "The customer describes broken or unexpected product behavior."
                  },
                  "instructions": "Is the customer reporting a software defect?",
                  "type": "noul"
                },
                "team": {
                  "criteria": {
                    "account": "Login, permissions, or profile issues.",
                    "payments": "Checkout, billing, or payment processing issues."
                  },
                  "instructions": "Which team should own this ticket?",
                  "type": "choice"
                },
                "urgency": {
                  "criteria": [
                    "Can wait for the next release",
                    "Should be fixed this week",
                    "Blocking revenue right now"
                  ],
                  "instructions": "How urgent is this ticket?",
                  "type": "score"
                }
              },
              "state": {
                "customer_tier": "enterprise",
                "ticket": "My checkout page shows a blank screen after I click Pay."
              }
            }
            """;

    @Test
    void bindsQuestionsStateAndModel() {
        DecisionsRequest request = JsonKit.fromJson(PAYLOAD, DecisionsRequest.class);

        assertNotNull(request);
        assertEquals("jev", request.model);
        assertEquals(3, request.questions.size(), "questions 必须按问题名原样承载");

        Map<String, Object> noul = questionOf(request, "is_bug");
        assertEquals("noul", noul.get("type"));
        assertEquals("Is the customer reporting a software defect?", noul.get("instructions"));
        // criteria 是开放键结构：noul 走 true/false 两分支
        @SuppressWarnings("unchecked")
        Map<String, Object> noulCriteria = (Map<String, Object>) noul.get("criteria");
        assertTrue(noulCriteria.containsKey("true"));
        assertTrue(noulCriteria.containsKey("false"));

        assertEquals("choice", questionOf(request, "team").get("type"));
        assertEquals("score", questionOf(request, "urgency").get("type"));
        // score 的 criteria 是有序数组，顺序即分值顺序，不能退化成 map
        assertTrue(questionOf(request, "urgency").get("criteria") instanceof List);

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) request.state;
        assertEquals("enterprise", state.get("customer_tier"));
    }

    @Test
    void stateAcceptsPlainStringAndArray() {
        DecisionsRequest withString = JsonKit.fromJson(
                "{\"state\":\"a plain ticket text\"}", DecisionsRequest.class);
        assertEquals("a plain ticket text", withString.state);

        DecisionsRequest withArray = JsonKit.fromJson(
                "{\"state\":[{\"turn\":\"first\"},{\"turn\":\"second\"}]}", DecisionsRequest.class);
        assertTrue(withArray.state instanceof List, "state 允许数组形态（多段上下文）");
    }

    @Test
    void sessionIdBindsSnakeCaseFieldBothWays() {
        DecisionsRequest request = JsonKit.fromJson(
                "{\"session_id\":\"session-1234\",\"user\":\"u-1\"}", DecisionsRequest.class);

        assertEquals("session-1234", request.sessionId);

        String json = JsonKit.toJson(request);
        assertTrue(json.contains("\"session_id\":\"session-1234\""), json);
        assertFalse(json.contains("\"sessionId\""), json);
    }

    @Test
    void omitsUnsetFieldsOnSerialization() {
        DecisionsRequest request = new DecisionsRequest();
        request.questions = Map.of("is_bug", Map.of("type", "noul", "instructions", "defect?"));
        request.state = "blank screen after Pay";

        String json = JsonKit.toJson(request);

        assertTrue(json.contains("\"questions\""), json);
        assertTrue(json.contains("\"state\""), json);
        assertFalse(json.contains("\"provider\""), json);
        assertFalse(json.contains("\"trace\""), json);
        assertFalse(json.contains("\"user\""), json);
        assertFalse(json.contains("\"session_id\""), json);
        assertFalse(json.contains("\"stream\""), "未声明 stream 时不得输出（上游不认识该字段）: " + json);
    }

    @Test
    void unknownFieldsAreCarriedThroughExtra() {
        DecisionsRequest request = JsonKit.fromJson(
                "{\"state\":\"s\",\"questions\":{},\"something_new\":{\"k\":1}}", DecisionsRequest.class);

        assertNotNull(request);
        assertEquals(1, request.getExtra().size(), "未知字段进基类 extra，转发时原样带出");
        assertNull(request.provider);

        String json = JsonKit.toJson(request);
        assertTrue(json.contains("\"something_new\""), json);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> questionOf(DecisionsRequest request, String name) {
        return (Map<String, Object>) request.questions.get(name);
    }
}
