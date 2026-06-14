package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.usage.calculator.SimpleBillingModeCalculator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ImageUsageExtractor extends DefaultUsageExtractor {

    public static final String CODE = "IMAGE";

    public ImageUsageExtractor(SimpleBillingModeCalculator calculator) {
        super(calculator);
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Image Usage Extractor";
    }

    @Override
    public BillingUsage extractUsage(Map<String, Object> body) {
        BillingUsage usage = super.extractUsage(body);
        if (!usage.isEmpty()) {
            return usage;
        }
        Object data = body.get("data");
        long amount = data instanceof Iterable<?> iterable ? count(iterable) : 0L;
        return amount > 0 ? new BillingUsage(0L, 0L, 0L, 0L, 0L, amount) : BillingUsage.empty();
    }

    private long count(Iterable<?> iterable) {
        long total = 0L;
        for (Object ignored : iterable) {
            total++;
        }
        return total;
    }
}
