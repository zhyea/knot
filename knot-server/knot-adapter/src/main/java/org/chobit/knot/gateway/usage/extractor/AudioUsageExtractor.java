package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.AudioBillingUsage;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.SimpleBillingModeCalculator;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class AudioUsageExtractor implements UsageExtractor<AudioBillingUsage> {

    public static final String CODE = "AUDIO";
    private final SimpleBillingModeCalculator calculator;

    public AudioUsageExtractor(SimpleBillingModeCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Audio Usage Extractor";
    }

    @Override
    public String description() {
        return "音频生成模型提取器：按时长（秒）计量，取响应体 usage 中上报的时长字段；token 分项内嵌保留，"
                + "上游若同时上报 token 用量不丢失。仅服务 AUDIO 计费模式。";
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 时长计量归 AudioBillingUsage，与 AUDIO 模式一一对应
        return EnumSet.of(BillingModeEnum.AUDIO);
    }

    @Override
    public AudioBillingUsage empty() {
        return AudioBillingUsage.empty();
    }

    @Override
    public AudioBillingUsage extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        TokenBillingUsage tokens = TokenBillingUsage.fromResponse(body);
        long durationSeconds = MediaUsageReaders.durationSeconds(MediaUsageReaders.rawUsage(body), body);
        return new AudioBillingUsage(tokens, durationSeconds);
    }
}
