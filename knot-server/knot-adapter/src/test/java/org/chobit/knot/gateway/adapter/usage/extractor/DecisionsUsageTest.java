package org.chobit.knot.gateway.adapter.usage.extractor;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.gateway.adapter.model.usage.BillingUsage;
import org.chobit.knot.gateway.adapter.usage.calculator.TokenBillingModeCalculator;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Decisions 协议（jev / AlphaDecisions）响应的用量口径。
 *
 * <p>该协议 {@code usage} 用 {@code input_tokens} / {@code output_tokens}（不是 OpenAI 的
 * prompt/completion 前缀），并额外带 {@code cost}。这里钉死默认 token 提取器能直接读到这两个
 * 别名——否则 jev 的计费会静默归零。{@code cost} 是上游口径，不参与 knot 计费（成本由自有
 * 计费规则计算），仅随原始 usage 透传。</p>
 */
class DecisionsUsageTest {

    /** 取自 OpenRouter AlphaDecisions API 参考文档的 200 响应示例 */
    private static final String RESPONSE = """
            {
              "answers": {
                "is_bug": {"noul": 0.96, "type": "noul"},
                "team": {"choice": "payments", "confidence": 0.75, "type": "choice"},
                "urgency": {"score": 1.99, "confidence": 0.99, "type": "score"}
              },
              "id": "gen-dec-1789738314-X5e5eKGQdvR9rblyX250",
              "model": "typesafe/jev-1.13-20260917",
              "provider": "TypeSafe",
              "usage": {"cost": 0.000019992, "input_tokens": 476, "output_tokens": 70}
            }
            """;

    private final DefaultTokenUsageExtractor extractor =
            new DefaultTokenUsageExtractor(new TokenBillingModeCalculator());

    @Test
    void readsInputAndOutputTokensFromDecisionsUsage() {
        Map<String, Object> body = JsonKit.fromJson(RESPONSE, new TypeReference<>() {
        });

        TokenBillingUsage usage = extractor.extractUsage(body).billing();

        assertEquals(476L, usage.inputTokens());
        assertEquals(70L, usage.outputTokens());
        assertEquals(546L, usage.totalTokens(), "上游未回 total，按输入+输出补齐");
        assertFalse(usage.isEmpty(), "用量为空会让计费与频控记账整体消失");
    }

    @Test
    void readsUsageFromRawResponseBody() {
        BillingUsageHolder holder = new BillingUsageHolder();
        holder.usage = extractor.extractUsageBody(RESPONSE, Map.of()).billing();

        assertFalse(holder.usage.isEmpty());
        assertEquals(476L, holder.usage.tokenUsage().inputTokens());
        assertEquals(70L, holder.usage.tokenUsage().outputTokens());
    }

    private static class BillingUsageHolder {
        private BillingUsage usage;
    }
}
