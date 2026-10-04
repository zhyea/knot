package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.SimpleBillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.TokenBillingModeCalculator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AnthropicUsageExtractor extends DefaultUsageExtractor {

    public static final String CODE = "ANTHROPIC";
    private final TokenBillingModeCalculator calculator;

    public AnthropicUsageExtractor(TokenBillingModeCalculator calculator, SimpleBillingModeCalculator defaultCalculator) {
        super(defaultCalculator);
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
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public BillingUsage extractUsage(Map<String, Object> body) {
        BillingUsage usage = super.extractUsage(body);
        if (usage.isEmpty()) {
            return usage;
        }
        // Anthropic 的 input_tokens 不含缓存读写，两侧相加才是输入侧 token 总量；
        // 缓存写走有效总量（ttl 明细优先），避免 5m/1h 口径下漏算
        long inputTokens = usage.inputTokens() + usage.cacheReadTokens() + usage.cacheWriteTotal();
        long totalTokens = Math.max(usage.totalTokens(), inputTokens + usage.outputTokens());
        return new BillingUsage(
                inputTokens,
                usage.outputTokens(),
                totalTokens,
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                usage.cacheWrite5mTokens(),
                usage.cacheWrite1hTokens(),
                usage.amount()
        );
    }
}
