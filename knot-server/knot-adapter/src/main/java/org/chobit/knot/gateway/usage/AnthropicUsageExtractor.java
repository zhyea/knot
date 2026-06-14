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
        long inputTokens = usage.inputTokens() + usage.cacheReadTokens() + usage.cacheWriteTokens();
        long totalTokens = Math.max(usage.totalTokens(), inputTokens + usage.outputTokens());
        return new BillingUsage(
                inputTokens,
                usage.outputTokens(),
                totalTokens,
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                usage.amount()
        );
    }
}
