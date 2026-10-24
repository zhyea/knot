package org.chobit.knot.gateway.upstream.usage;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.config.UsageExtractionProperties;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.model.usage.UsageExtractItem;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.TokenBillingModeCalculator;
import org.chobit.knot.gateway.usage.extractor.DefaultTokenUsageExtractor;
import org.chobit.knot.gateway.usage.extractor.UsageExtractor;
import org.chobit.knot.gateway.usage.extractor.UsageExtractorCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用量提取的失败语义（2026-10-23 计费去兜底）：
 *
 * <p>删除适配器兜底后，{@link UsageExtractorRegistry} 是唯一入口，错误必须<b>显式暴露</b>——
 * 不允许再把配置错误或解析失败降级成「空用量 / 零计费」。</p>
 *
 * <p>空用量按三分支处置，本类逐条钉死：</p>
 * <ol>
 *   <li>提取器返回 {@code null} → 契约错误；</li>
 *   <li>上游没回 usage（或全零）→ 合法零用量，不失败；</li>
 *   <li>上游回了非零 usage 却提取不出来 → 口径错配，失败。</li>
 * </ol>
 *
 * <p>失败是否中断响应由 {@code knot.gateway.usage.degrade-on-failure} 决定（默认 {@code false}）：
 * 三处提取失败收敛到同一个处置点，故只需把两种取值各验一次，不必按失败原因逐个验。
 * 但<b>提取器不存在不受开关影响</b>，单独钉住。</p>
 */
class UsageExtractorRegistryTest {

    private static final String STUB_CODE = "STUB-EXTRACTOR";

    @Test
    @DisplayName("未知 extractor 编码立即失败，错误信息可定位到具体 code")
    void failsFastOnUnknownExtractorCode() {
        ModelApiBindingEntity binding = new ModelApiBindingEntity();
        binding.setUsageExtractor("NOT-A-REAL-EXTRACTOR");
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> registry.requireExtractor(false, context(binding)),
                "配了解析不出的 extractor 必须在请求开始前就失败，不允许沉到空用量里");

        assertTrue(thrown.getMessage().contains("NOT-A-REAL-EXTRACTOR"),
                "错误信息必须带上具体编码，否则运维无从定位：" + thrown.getMessage());
    }

    @Test
    @DisplayName("未配置 extractor 走默认提取器，不算错误")
    void fallsBackToDefaultExtractorWhenUnconfigured() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()));

        assertEquals(DefaultTokenUsageExtractor.CODE,
                registry.requireExtractor(false, context(null)).code(),
                "未配置不是错误——resolveExtractorCode 末行恒回落默认提取器");
    }

    @Test
    @DisplayName("提取器返回 null 视为契约错误，不降级成空用量")
    void failsWhenExtractorReturnsNull() {
        UsageExtractorRegistry registry = registry(new StubExtractor(null));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> registry.extract("{\"id\":\"1\"}", context(binding(STUB_CODE))));

        assertTrue(thrown.getMessage().contains(STUB_CODE),
                "契约错误必须点名是哪个提取器：" + thrown.getMessage());
    }

    @Test
    @DisplayName("上游未上报 usage：合法零用量，不失败")
    void acceptsLegalZeroUsageWhenUpstreamReportsNothing() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()));

        UsageAccounting accounting = registry.extract("{\"id\":\"1\"}", context(binding(STUB_CODE)));

        assertNotNull(accounting, "上游没回 usage 是合法协议行为，不能把请求打失败");
        assertTrue(accounting.billing().isEmpty());
    }

    @Test
    @DisplayName("上游上报全零 usage：同属合法零用量，不失败")
    void acceptsLegalZeroUsageWhenUpstreamReportsAllZero() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()));

        UsageAccounting accounting =
                registry.extract("{\"usage\":{\"input_tokens\":0,\"output_tokens\":0}}", context(binding(STUB_CODE)));

        assertNotNull(accounting, "上游明确报零不等于网关解析不出来，不应判为口径错配");
    }

    @Test
    @DisplayName("上游回了非零 usage 却提取为空：口径错配，必须失败")
    void failsOnExtractorMismatch() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> registry.extract("{\"usage\":{\"input_tokens\":120,\"output_tokens\":30}}",
                        context(binding(STUB_CODE))),
                "有真实用量却提取不出来 = 提取器配错，静默记零账会把问题推迟到对账");

        assertTrue(thrown.getMessage().contains(STUB_CODE), thrown.getMessage());
    }

    @Test
    @DisplayName("提取器声明允许全零：即使上游 usage 有正数也放行")
    void allowsEmptyUsageWhenExtractorDeclaresIt() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem(), true));

        UsageAccounting accounting = registry.extract(
                "{\"usage\":{\"input_tokens\":120,\"output_tokens\":30}}", context(binding(STUB_CODE)));

        assertNotNull(accounting, "允许全零由提取器声明，注册表不再据「有正数」跨协议判错");
        assertTrue(accounting.billing().isEmpty());
    }

    @Test
    @DisplayName("camelCase usage 配默认提取器：属口径错配，必须失败")
    void failsOnCamelCaseUsageWithDefaultExtractor() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()));
        ModelApiBindingEntity binding = binding(DefaultTokenUsageExtractor.CODE);

        assertThrows(IllegalStateException.class,
                () -> registry.extract("{\"usage\":{\"inputTokens\":120}}", context(binding)),
                "camelCase 用量只有专用提取器读得出来，默认提取器读出空必须暴露而非记零账");
    }

    @Test
    @DisplayName("上游响应非法 JSON：严格失败，不与「未上报 usage」混淆")
    void failsOnMalformedJson() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> registry.extract("{not-json-at-all", context(binding(STUB_CODE))),
                "解析失败曾被引擎吞成 null，与「上游没回 usage」无法区分");

        assertTrue(thrown.getMessage().contains("json"), thrown.getMessage());
    }

    @Test
    @DisplayName("开启 degrade-on-failure 后口径错配不再中断：出零账并保留上游原文证据")
    void degradesInsteadOfFailingWhenConfigured() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()), degrade());

        UsageAccounting accounting = registry.extract(
                "{\"usage\":{\"input_tokens\":120,\"output_tokens\":30}}", context(binding(STUB_CODE)));

        assertNotNull(accounting, "降级开启时提取失败必须放行，否则响应还是被打断");
        assertTrue(accounting.billing().isEmpty(), "放行不等于猜用量：计费输入必须为空（零账）");
        assertNull(accounting.normalized(), "零账不应带计费结果");
        assertEquals(120, accounting.rawUsage().get("input_tokens"),
                "上游原文必须保留——它是事后重算的唯一依据，降级不能把它一起丢掉");
    }

    @Test
    @DisplayName("开启 degrade-on-failure 后提取器返回 null 同样降级，不中断")
    void degradesWhenExtractorReturnsNull() {
        UsageExtractorRegistry registry = registry(new StubExtractor(null), degrade());

        UsageAccounting accounting = registry.extract("{\"id\":\"1\"}", context(binding(STUB_CODE)));

        assertNotNull(accounting, "契约错误在 degrade 档下同样只记 ERROR 不中断");
        assertTrue(accounting.billing().isEmpty());
    }

    @Test
    @DisplayName("开启 degrade-on-failure 后非法 JSON 也降级：原文读不出来，账目全空")
    void degradesOnMalformedJson() {
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()), degrade());

        UsageAccounting accounting = registry.extract("{not-json-at-all", context(binding(STUB_CODE)));

        assertNotNull(accounting, "降级开启时解析失败必须放行");
        assertTrue(accounting.isEmpty(), "原文都读不出来，只能出全空账目");
    }

    @Test
    @DisplayName("degrade 不放过未知 extractor 编码：配置错误恒 fail-fast")
    void keepsFailingOnUnknownExtractorEvenWhenDegradeConfigured() {
        ModelApiBindingEntity binding = new ModelApiBindingEntity();
        binding.setUsageExtractor("NOT-A-REAL-EXTRACTOR");
        UsageExtractorRegistry registry = registry(new StubExtractor(emptyItem()), degrade());

        assertThrows(IllegalStateException.class,
                () -> registry.requireExtractor(false, context(binding)),
                "未知 code 是配置错误而非提取失败，降级它等于该绑定永久零账且无自愈路径");
    }

    @Test
    @DisplayName("不显式配置时默认不降级：提取失败必须中断响应")
    void failsByDefaultWhenNotConfigured() {
        assertFalse(new UsageExtractionProperties().isDegradeOnFailure(),
                "出厂默认必须让失败可见——静默零账只在对账时才暴露；"
                        + "宽松行为必须显式打开，不能靠字段初始化兜");
    }

    private static UsageExtractorRegistry registry(UsageExtractor<?> stub) {
        return registry(stub, new UsageExtractionProperties());
    }

    private static UsageExtractorRegistry registry(UsageExtractor<?> stub, UsageExtractionProperties properties) {
        List<UsageExtractor<?>> extractors = List.of(
                new DefaultTokenUsageExtractor(new TokenBillingModeCalculator()), stub);
        return new UsageExtractorRegistry(new UsageExtractorCatalog(extractors), null, properties);
    }

    private static UsageExtractionProperties degrade() {
        UsageExtractionProperties properties = new UsageExtractionProperties();
        properties.setDegradeOnFailure(true);
        return properties;
    }

    private static ModelApiBindingEntity binding(String extractorCode) {
        ModelApiBindingEntity binding = new ModelApiBindingEntity();
        binding.setUsageExtractor(extractorCode);
        return binding;
    }

    private static UpstreamRequestContext context(ModelApiBindingEntity binding) {
        return new UpstreamRequestContext(ModelApiProtocolEnum.CHAT_COMPLETIONS, Map.of(),
                null, null, null, null, binding, null, null);
    }

    private static UsageExtractItem<TokenBillingUsage> emptyItem() {
        return UsageExtractItem.of(TokenBillingUsage.empty());
    }

    /**
     * 提取器替身：按用例返回固定结果（含 null），用于驱动 registry 的各条失败分支。
     *
     * <p>JDK 25 + Mockito inline 不能 mock 具体类，故手写实现而非 mock。</p>
     */
    private static final class StubExtractor implements UsageExtractor<TokenBillingUsage> {

        private final UsageExtractItem<TokenBillingUsage> result;
        private final boolean emptyAllowed;

        private StubExtractor(UsageExtractItem<TokenBillingUsage> result) {
            this(result, false);
        }

        private StubExtractor(UsageExtractItem<TokenBillingUsage> result, boolean emptyAllowed) {
            this.result = result;
            this.emptyAllowed = emptyAllowed;
        }

        @Override
        public boolean allowsEmptyUsage() {
            return emptyAllowed;
        }

        @Override
        public String code() {
            return STUB_CODE;
        }

        @Override
        public String label() {
            return "stub";
        }

        @Override
        public String description() {
            return "可控返回值的提取器替身";
        }

        @Override
        public BillingModeCalculator calculator() {
            return new TokenBillingModeCalculator();
        }

        @Override
        public Set<BillingModeEnum> billingModes() {
            return Set.of(BillingModeEnum.TOKEN);
        }

        @Override
        public UsageExtractItem<TokenBillingUsage> empty() {
            return emptyItem();
        }

        @Override
        public UsageExtractItem<TokenBillingUsage> extractUsage(Map<String, Object> body,
                                                                Map<String, Object> requestBody) {
            return result;
        }
    }
}
