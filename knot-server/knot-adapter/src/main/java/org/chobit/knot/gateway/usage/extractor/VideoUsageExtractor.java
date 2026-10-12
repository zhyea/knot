package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.VideoBillingUsage;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.VideoBillingModeCalculator;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class VideoUsageExtractor implements UsageExtractor<VideoBillingUsage> {

    public static final String CODE = "VIDEO";
    private final VideoBillingModeCalculator calculator;

    public VideoUsageExtractor(VideoBillingModeCalculator calculator) {
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
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 时长/像素/声音/参考归 VideoBillingUsage，与 VIDEO 模式一一对应
        return EnumSet.of(BillingModeEnum.VIDEO);
    }

    @Override
    public VideoBillingUsage empty() {
        return VideoBillingUsage.empty();
    }

    @Override
    public VideoBillingUsage extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        TokenBillingUsage tokens = TokenBillingUsage.fromResponse(body);
        long durationSeconds = MediaUsageReaders.durationSeconds(MediaUsageReaders.rawUsage(body), body);
        MediaUsageReaders.MediaSize size = MediaUsageReaders.sizeOf(requestBody);
        boolean sound = MediaUsageReaders.flag(requestBody,
                "generate_audio", "with_audio", "enable_audio", "audio_enabled", "sound");
        boolean reference = MediaUsageReaders.flag(requestBody,
                "input_reference", "reference", "reference_image", "reference_video", "reference_url", "reference_images");
        return new VideoBillingUsage(tokens, durationSeconds,
                size.pixels(), size.width(), size.height(), sound, reference);
    }
}
