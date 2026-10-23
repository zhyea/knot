package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.usage.UsageExtractItem;
import org.chobit.knot.gateway.usage.ModelUsageNormalizer;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.TokenBillingModeCalculator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
@Order(0)
public class DefaultTokenUsageExtractor implements UsageExtractor<TokenBillingUsage> {

    public static final String CODE = "DEFAULT-TOKEN";
    private final TokenBillingModeCalculator calculator;

    public DefaultTokenUsageExtractor(TokenBillingModeCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Default Token Usage Extractor";
    }

    @Override
    public String description() {
        return "通用 token 用量提取器：按 OpenAI 口径从上游响应 usage 读取输入、输出、缓存读写等 "
            + "token 分项，适配未单独定制提取器的模型。作为兜底候选覆盖全部计费模式——Simple 计算器 "
            + "按规则所配计费模式分摄取量，token 记录与媒体（图片张数、视频/音频时长）指标各走各的计量。";
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 通用兜底：Simple 计算器按规则模式分摄取量（token/请求/媒体计量全走 switch），全部模式均可服务
        return EnumSet.allOf(BillingModeEnum.class);
    }

    @Override
    public UsageExtractItem<TokenBillingUsage> empty() {
        return UsageExtractItem.of(TokenBillingUsage.empty());
    }

    @Override
    public UsageExtractItem<TokenBillingUsage> extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        TokenBillingUsage billing = TokenBillingUsage.fromResponse(body);
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }
}
