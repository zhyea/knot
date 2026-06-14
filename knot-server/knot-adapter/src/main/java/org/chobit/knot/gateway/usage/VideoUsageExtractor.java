package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.usage.calculator.SimpleBillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.VideoBillingModeCalculator;
import org.chobit.knot.gateway.util.MapNumberUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VideoUsageExtractor extends DefaultUsageExtractor {

    public static final String CODE = "VIDEO";
    private final VideoBillingModeCalculator calculator;

    public VideoUsageExtractor(VideoBillingModeCalculator calculator, SimpleBillingModeCalculator defaultCalculator) {
        super(defaultCalculator);
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Video Usage Extractor";
    }

    @Override
    public VideoBillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public BillingUsage extractUsage(Map<String, Object> body) {
        BillingUsage usage = super.extractUsage(body);
        if (!usage.isEmpty()) {
            return usage;
        }
        long seconds = MapNumberUtils.firstLong(body, "duration_seconds", "video_seconds", "seconds", "duration");
        return seconds > 0 ? new BillingUsage(0L, 0L, 0L, 0L, 0L, seconds) : BillingUsage.empty();
    }
}
