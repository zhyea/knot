package org.chobit.knot.svc.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * 外部模型转统一模型时的编码归一规则（robin 2026-11-10）：
 * 点号是版本号的一部分，保留；斜杠左侧是供应商命名空间，丢弃。
 * 主干与冲突后缀共用同一口径，避免只有一处生效。
 */
class ExternalModelServiceCodeTest {

    @Test
    void keepsDotsInsteadOfReplacingThemWithHyphens() {
        assertEquals("openai-gpt-4.1", ExternalModelService.slug("OpenAI: GPT-4.1"));
        assertEquals("gemini-2.5-pro", ExternalModelService.slug("google/gemini-2.5-pro"));
    }

    @Test
    void dropsEverythingLeftOfLastSlash() {
        assertEquals("deepseek-chat", ExternalModelService.slug("deepseek/deepseek-chat"));
        assertEquals("c", ExternalModelService.slug("a/b/c"));
    }

    @Test
    void trimsEdgePunctuationAndCollapsesRepeatedDots() {
        assertEquals("gpt-4.1", ExternalModelService.slug("  ..GPT 4.1..  "));
        assertEquals("v1.2", ExternalModelService.slug("v1..2"));
    }

    @Test
    void emptyWhenNothingUsableRemains() {
        assertEquals("", ExternalModelService.slug("模型"));
        assertEquals("", ExternalModelService.slug(null));
        assertEquals("", ExternalModelService.slug("openai/"));
    }

    @Test
    void capsBaseLength() {
        assertEquals(96, ExternalModelService.slug("a".repeat(200)).length());
    }

    @Test
    void suffixUsesSameNormalization() {
        assertEquals("gpt-4", ExternalModelService.modelKeySuffix("openai/gpt-4.1"));
        assertEquals("qwen3", ExternalModelService.modelKeySuffix("qwen/qwen3"));
        assertEquals("", ExternalModelService.modelKeySuffix(null));
    }
}
