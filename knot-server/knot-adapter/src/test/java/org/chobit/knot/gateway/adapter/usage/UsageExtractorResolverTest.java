package org.chobit.knot.gateway.adapter.usage;

import org.chobit.knot.gateway.adapter.model.ref.ModelApiBindingRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 提取器选择的单一来源（改进清单 3.4）：网关 {@code UsageExtractorRegistry} 与管理端
 * 裁判 {@code RoutingTestBillingVerdictService} 共用本类，消灭两份同口径实现。
 *
 * <p>选择口径（2026-10-24 2.2b 移除协议/供应商猜测后）：<b>绑定显式配置是唯一来源</b>，
 * 流式优先取 {@code streamUsageExtractor}，未配置返回 {@code null} 由调用方回落默认——
 * 默认提取器在各侧形态不同（网关有 catalog、裁判是常量），故回落不放在本类。</p>
 */
class UsageExtractorResolverTest {

    private static ModelApiBindingRef binding(String usageExtractor, String streamUsageExtractor) {
        return new ModelApiBindingRef(null, null, "CHAT_COMPLETIONS",
                usageExtractor, streamUsageExtractor);
    }

    @Test
    @DisplayName("绑定显式配置的提取器被选中")
    void resolvesConfiguredExtractor() {
        assertEquals("DEFAULT-TOKEN",
                UsageExtractorResolver.fromBinding(binding("DEFAULT-TOKEN", null), false));
    }

    @Test
    @DisplayName("流式优先取 streamUsageExtractor")
    void prefersStreamExtractorWhenEventStream() {
        assertEquals("STREAM-EXTRACTOR",
                UsageExtractorResolver.fromBinding(binding("DEFAULT-TOKEN", "STREAM-EXTRACTOR"), true));
    }

    @Test
    @DisplayName("流式但未配 stream 提取器时回落 usageExtractor")
    void fallsBackToUsageExtractorWhenStreamNotConfigured() {
        assertEquals("DEFAULT-TOKEN",
                UsageExtractorResolver.fromBinding(binding("DEFAULT-TOKEN", null), true));
    }

    @Test
    @DisplayName("流式但整包提取器也未配置：不猜，返回 null 交调用方回落")
    void returnsNullWhenNothingConfigured() {
        assertNull(UsageExtractorResolver.fromBinding(binding(null, null), true));
        assertNull(UsageExtractorResolver.fromBinding(binding("  ", "  "), false),
                "空白串等同未配置");
    }

    @Test
    @DisplayName("未绑定（binding 为 null）不猜：返回 null")
    void returnsNullWhenBindingAbsent() {
        assertNull(UsageExtractorResolver.fromBinding(null, false));
    }

    @Test
    @DisplayName("裁判形态：按 protocol 匹配一组绑定，取第一个显式配置")
    void resolvesFromBindingListByProtocol() {
        List<ModelApiBindingRef> bindings = List.of(
                new ModelApiBindingRef(null, null, "IMAGE_GENERATIONS", null, null),
                new ModelApiBindingRef(null, null, "IMAGE_EDITS", "IMAGE", null));

        assertEquals("IMAGE",
                UsageExtractorResolver.fromBindings(bindings, "IMAGE_EDITS", false),
                "同协议两个绑定时跳过未配置的那个，取显式配置的");
    }

    @Test
    @DisplayName("裁判形态：无匹配或无显式配置时返回 null，不猜")
    void returnsNullFromBindingListWhenNoMatch() {
        List<ModelApiBindingRef> bindings = List.of(
                new ModelApiBindingRef(null, null, "IMAGE_GENERATIONS", null, null));

        assertNull(UsageExtractorResolver.fromBindings(bindings, "CHAT_COMPLETIONS", false));
        assertNull(UsageExtractorResolver.fromBindings(bindings, null, false));
        assertNull(UsageExtractorResolver.fromBindings(List.of(), "IMAGE_GENERATIONS", false));
        assertNull(UsageExtractorResolver.fromBindings(null, "IMAGE_GENERATIONS", false));
    }

    @Test
    @DisplayName("裁判形态：流式同样优先 stream 提取器")
    void prefersStreamExtractorInBindingList() {
        List<ModelApiBindingRef> bindings = List.of(
                new ModelApiBindingRef(null, null, "CHAT_COMPLETIONS", "DEFAULT-TOKEN", "STREAM-EXTRACTOR"));

        assertEquals("STREAM-EXTRACTOR",
                UsageExtractorResolver.fromBindings(bindings, "CHAT_COMPLETIONS", true));
    }
}
