package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.model.BillingUsage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AnthropicUsageExtractor extends DefaultUsageExtractor {

    public static final String CODE = "ANTHROPIC";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Anthropic Usage Extractor";
    }

    @Override
    public BillingUsage extract(Map<String, Object> body) {
        BillingUsage usage = super.extract(body);
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
