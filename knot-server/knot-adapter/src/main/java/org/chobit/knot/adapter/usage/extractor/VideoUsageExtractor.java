package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.UsageExtractItem;
import org.chobit.knot.adapter.usage.ModelUsageNormalizer;
import org.chobit.knot.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.VideoBillingModeCalculator;
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
    public String description() {
        return "视频生成模型提取器：输出时长取响应体 usage 的秒数字段；输入视频时长、输入图片张数、"
                + "像素尺寸、是否有声音（generate_audio 等）、是否有参考来源（input_reference 等）"
                + "上游响应不回传，全部从请求体解析后随用量带入计价——它们既参与维度分档键，"
                + "也支撑合成量与多分量求和两种业界口径。token 分项内嵌保留。仅服务 VIDEO 计费模式。";
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
    public UsageExtractItem<VideoBillingUsage> empty() {
        return UsageExtractItem.of(VideoBillingUsage.empty());
    }

    @Override
    public UsageExtractItem<VideoBillingUsage> extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        TokenBillingUsage tokens = TokenBillingUsage.fromResponse(body);
        long durationSeconds = MediaUsageReaders.durationSeconds(MediaUsageReaders.rawUsage(body), body);
        long inputDurationSeconds = MediaUsageReaders.inputDurationSeconds(requestBody);
        long imageCount = MediaUsageReaders.imageCount(requestBody);
        MediaUsageReaders.MediaSize size = MediaUsageReaders.sizeOf(requestBody);
        boolean sound = MediaUsageReaders.flag(requestBody,
                "generate_audio", "with_audio", "enable_audio", "audio_enabled", "sound");
        boolean reference = MediaUsageReaders.flag(requestBody,
                "input_reference", "reference", "reference_image", "reference_video", "reference_url", "reference_images");
        VideoBillingUsage billing = new VideoBillingUsage(tokens, durationSeconds, inputDurationSeconds, imageCount,
                size.pixels(), size.width(), size.height(), sound, reference);
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }
}
