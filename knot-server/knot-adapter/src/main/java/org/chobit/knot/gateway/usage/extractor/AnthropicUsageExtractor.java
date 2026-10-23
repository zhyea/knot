package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.usage.UsageExtractItem;
import org.chobit.knot.gateway.usage.ModelUsageNormalizer;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.TokenBillingModeCalculator;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class AnthropicUsageExtractor implements UsageExtractor<TokenBillingUsage> {

    public static final String CODE = "ANTHROPIC";
    private final TokenBillingModeCalculator calculator;

    public AnthropicUsageExtractor(TokenBillingModeCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Anthropic Usage Extractor";
    }

    @Override
    public String description() {
        return "Anthropic Messages API 专用提取器：按 Anthropic 口径提取 input/output token 与 "
                + "cache_creation、cache_read 明细。Anthropic 的 usage 上报不含缓存读写 token，"
                + "本提取器将其加回输入侧总量后再计费，与上游口径保持一致。仅服务 TOKEN 计费模式。";
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 只出 token 口径；Token 计算器遇媒体用量按错配零出账，配非 TOKEN 规则无意义
        return EnumSet.of(BillingModeEnum.TOKEN);
    }

    @Override
    public UsageExtractItem<TokenBillingUsage> empty() {
        return UsageExtractItem.of(TokenBillingUsage.empty());
    }

    @Override
    public UsageExtractItem<TokenBillingUsage> extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        // Anthropic 口径：缓存读写加回输入侧总量（与 AnthropicRequestAdapter 同一出口）
        TokenBillingUsage billing = TokenBillingUsage.fromResponse(body).withCacheCountedInInput();
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }
}
