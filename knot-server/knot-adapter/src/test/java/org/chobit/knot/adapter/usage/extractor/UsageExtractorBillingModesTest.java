package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.adapter.usage.extractor.*;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.adapter.usage.UsageExtractorDefinition;
import org.chobit.knot.adapter.usage.calculator.SimpleBillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.TokenBillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.VideoBillingModeCalculator;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * billingModes 声明契约：解析器与计费模式一一对应，DEFAULT 兜底全部模式。
 *
 * <p>前端 Usage 解析器下拉按已绑计费规则的 {@code billingMode} 收敛候选，依赖本声明；
 * 新增提取器忘记声明时，本测试兜底暴露。</p>
 */
class UsageExtractorBillingModesTest {

    private final SimpleBillingModeCalculator simple = new SimpleBillingModeCalculator();
    private final TokenBillingModeCalculator token = new TokenBillingModeCalculator();
    private final VideoBillingModeCalculator video = new VideoBillingModeCalculator();

    private final List<UsageExtractor<?>> extractors = List.of(
            new DefaultTokenUsageExtractor(token),
            new AnthropicUsageExtractor(token),
            new ImageUsageExtractor(simple),
            new VideoUsageExtractor(video),
            new AudioUsageExtractor(simple)
    );

    @Test
    void declaresModesOneToOneWithSealedType() {
        // DEFAULT 是通用兜底：Simple 计算器按规则模式分摄取量，声明全部模式
        assertEquals(EnumSet.allOf(BillingModeEnum.class), new DefaultTokenUsageExtractor(token).billingModes());
        assertEquals(EnumSet.of(BillingModeEnum.TOKEN), new AnthropicUsageExtractor(token).billingModes());
        assertEquals(EnumSet.of(BillingModeEnum.IMAGE), new ImageUsageExtractor(simple).billingModes());
        assertEquals(EnumSet.of(BillingModeEnum.VIDEO), new VideoUsageExtractor(video).billingModes());
        assertEquals(EnumSet.of(BillingModeEnum.AUDIO), new AudioUsageExtractor(simple).billingModes());
    }

    @Test
    void catalogDefinitionsCarryBillingModes() {
        UsageExtractorCatalog catalog = new UsageExtractorCatalog(extractors);
        Map<String, Set<BillingModeEnum>> modesByCode = new HashMap<>();
        for (UsageExtractorDefinition definition : catalog.definitions()) {
            modesByCode.put(definition.code(), definition.billingModes());
        }
        // code 一律取实现常量，避免 ff09840 改名后字面量不同步（本次修复的回归点）
        assertEquals(EnumSet.allOf(BillingModeEnum.class), modesByCode.get(DefaultTokenUsageExtractor.CODE));
        assertEquals(EnumSet.of(BillingModeEnum.TOKEN), modesByCode.get(AnthropicUsageExtractor.CODE));
        assertEquals(EnumSet.of(BillingModeEnum.IMAGE), modesByCode.get(ImageUsageExtractor.CODE));
        assertEquals(EnumSet.of(BillingModeEnum.VIDEO), modesByCode.get(VideoUsageExtractor.CODE));
        assertEquals(EnumSet.of(BillingModeEnum.AUDIO), modesByCode.get(AudioUsageExtractor.CODE));
    }
}
