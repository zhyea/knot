package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.ImageBillingUsage;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.SimpleBillingModeCalculator;
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
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 张数计量归 ImageBillingUsage，与 IMAGE 模式一一对应
        return EnumSet.of(BillingModeEnum.IMAGE);
    }

    @Override
    public ImageBillingUsage empty() {
        return ImageBillingUsage.empty();
    }

    @Override
    public ImageBillingUsage extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        TokenBillingUsage tokens = TokenBillingUsage.fromResponse(body);
        Map<String, Object> usage = MediaUsageReaders.rawUsage(body);
        // 张数：响应体 data 数组优先，usage 里的别名字段兜底
        long count = MediaUsageReaders.countData(body);
        if (count <= 0) {
            count = MapNumberUtils.firstLong(usage, "image_count", "images", "n", "num_images");
        }
        MediaUsageReaders.MediaSize size = MediaUsageReaders.sizeOf(requestBody);
        return new ImageBillingUsage(tokens, count, size.pixels(), size.width(), size.height());
    }
}
