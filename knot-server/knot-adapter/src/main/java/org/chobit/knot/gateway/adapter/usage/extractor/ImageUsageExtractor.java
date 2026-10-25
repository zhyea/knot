package org.chobit.knot.gateway.adapter.usage.extractor;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.gateway.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.gateway.adapter.usage.UsageExtractItem;
import org.chobit.knot.gateway.adapter.usage.ModelUsageNormalizer;
import org.chobit.knot.gateway.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.adapter.usage.calculator.SimpleBillingModeCalculator;
import org.chobit.knot.gateway.util.MapNumberUtils;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class ImageUsageExtractor implements UsageExtractor<ImageBillingUsage> {

    public static final String CODE = "IMAGE";
    private final SimpleBillingModeCalculator calculator;

    public ImageUsageExtractor(SimpleBillingModeCalculator calculator) {
        this.calculator = calculator;
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
    public String description() {
        return "图片生成模型提取器：张数优先取响应体 data 数组长度，usage 里的 image_count 等别名字段兜底；"
                + "像素尺寸（size）上游不回传，从请求体解析并随用量一起带入计价。token 分项内嵌保留，"
                + "双报模型（如 gpt-image-1 同时上报图片与 token 用量）不丢量。仅服务 IMAGE 计费模式。";
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 张数计量归 ImageBillingUsage，与 IMAGE 模式一一对应
        return EnumSet.of(BillingModeEnum.IMAGE);
    }

    @Override
    public UsageExtractItem<ImageBillingUsage> empty() {
        return UsageExtractItem.of(ImageBillingUsage.empty());
    }

    @Override
    public UsageExtractItem<ImageBillingUsage> extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        TokenBillingUsage tokens = TokenBillingUsage.fromResponse(body);
        Map<String, Object> usage = MediaUsageReaders.rawUsage(body);
        // 张数：响应体 data 数组优先，usage 里的别名字段兜底
        long count = MediaUsageReaders.countData(body);
        if (count <= 0) {
            count = MapNumberUtils.firstLong(usage, "image_count", "images", "n", "num_images");
        }
        MediaUsageReaders.MediaSize size = MediaUsageReaders.sizeOf(requestBody);
        ImageBillingUsage billing = new ImageBillingUsage(tokens, count, size.pixels(), size.width(), size.height());
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }
}
