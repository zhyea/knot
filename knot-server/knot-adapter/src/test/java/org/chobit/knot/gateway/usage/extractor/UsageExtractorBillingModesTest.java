package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.usage.UsageExtractorDefinition;
import org.chobit.knot.gateway.usage.calculator.SimpleBillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.TokenBillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.VideoBillingModeCalculator;
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
            new DefaultTokenUsageExtractor(simple),
            new AnthropicUsageExtractor(token),
            new ImageUsageExtractor(simple),
            new VideoUsageExtractor(video),
            new AudioUsageExtractor(simple)
    );

    @Test
    void declaresModesOneToOneWithSealedType() {
        // DEFAULT 是通用兜底：Simple 计算器按规则模式分摄取量，声明全部模式
        assertEquals(EnumSet.allOf(BillingModeEnum.class), new DefaultTokenUsageExtractor(simple).billingModes());
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
        assertEquals(EnumSet.allOf(BillingModeEnum.class), modesByCode.get("DEFAULT"));
        assertEquals(EnumSet.of(BillingModeEnum.TOKEN), modesByCode.get("ANTHROPIC"));
        assertEquals(EnumSet.of(BillingModeEnum.IMAGE), modesByCode.get("IMAGE"));
        assertEquals(EnumSet.of(BillingModeEnum.VIDEO), modesByCode.get("VIDEO"));
        assertEquals(EnumSet.of(BillingModeEnum.AUDIO), modesByCode.get("AUDIO"));
    }
}
