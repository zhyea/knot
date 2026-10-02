package org.chobit.knot.gateway.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelFamilyResolverTest {

    private static final List<String> CODES =
            List.of("gpt", "claude", "deepseek", "gemini", "gemma", "grok", "llama",
                    "mistral", "cohere", "phi", "qwen", "glm", "minimax", "hailuo", "moonshot");

    @Test
    void resolvesOpenAiGpt() {
        assertEquals("gpt", ModelFamilyResolver.matchFamily("openai/gpt-4o", "GPT-4o", CODES));
    }

    @Test
    void resolvesAnthropicClaude() {
        assertEquals("claude", ModelFamilyResolver.matchFamily("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", CODES));
    }

    @Test
    void resolvesDeepSeek() {
        assertEquals("deepseek", ModelFamilyResolver.matchFamily("deepseek/deepseek-chat", "DeepSeek Chat", CODES));
    }

    @Test
    void resolvesGoogleGemini() {
        assertEquals("gemini", ModelFamilyResolver.matchFamily("google/gemini-2.0-flash", "Gemini 2.0 Flash", CODES));
    }

    @Test
    void resolvesGoogleGemma() {
        assertEquals("gemma", ModelFamilyResolver.matchFamily("google/gemma-7b", "Gemma 7B", CODES));
    }

    @Test
    void resolvesMetaLlama() {
        assertEquals("llama", ModelFamilyResolver.matchFamily("meta-llama/llama-3.1-70b", "Llama 3.1 70B", CODES));
    }

    @Test
    void resolvesMistral() {
        assertEquals("mistral", ModelFamilyResolver.matchFamily("mistralai/mistral-7b", "Mistral 7B", CODES));
    }

    @Test
    void resolvesZhipuGlm() {
        assertEquals("glm", ModelFamilyResolver.matchFamily("zhipu/glm-4", "GLM-4", CODES));
    }

    @Test
    void resolvesMoonshot() {
        assertEquals("moonshot", ModelFamilyResolver.matchFamily("moonshot/moonshot-v1", "Moonshot v1", CODES));
    }

    @Test
    void returnsNullWhenNoMatch() {
        assertNull(ModelFamilyResolver.matchFamily("cohere/command-r", "Command R", CODES));
        assertNull(ModelFamilyResolver.matchFamily("openai/o1-preview", "O1 Preview", CODES));
    }

    @Test
    void returnsNullWhenCodesEmpty() {
        assertNull(ModelFamilyResolver.matchFamily("openai/gpt-4o", "GPT-4o", List.of()));
        assertNull(ModelFamilyResolver.matchFamily(null, null, null));
    }

    @Test
    void prefersLongerCodeOnOverlap() {
        // 若未来出现可重叠的码，最长码优先
        List<String> overlapping = List.of("mistral", "mixtral");
        assertEquals("mixtral", ModelFamilyResolver.matchFamily("mistralai/mixtral-8x7b", "Mixtral 8x7B", overlapping));
    }
}
