package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.model.BillingUsage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultUsageExtractor implements UsageExtractor {

    public static final String CODE = "DEFAULT";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Default Usage Extractor";
    }

    @Override
    @SuppressWarnings("unchecked")
    public BillingUsage extractUsage(Map<String, Object> body) {
        Object usage = body.get(AiPayloadFields.USAGE);
        if (usage instanceof Map<?, ?> map) {
            return BillingUsage.from((Map<String, Object>) map);
        }
        return BillingUsage.empty();
    }
}
