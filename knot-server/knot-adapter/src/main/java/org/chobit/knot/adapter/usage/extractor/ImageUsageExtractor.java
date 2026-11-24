package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.ImageTokenUsage;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.usage.UsageExtractItem;
import org.chobit.knot.adapter.usage.UsageRawReader;
import org.chobit.knot.adapter.usage.ModelUsageNormalizer;
import org.chobit.knot.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.ImageBillingModeCalculator;
import org.chobit.knot.common.util.MapNumberUtils;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class ImageUsageExtractor implements UsageExtractor<ImageBillingUsage> {

    public static final String CODE = "IMAGE";
    private final ImageBillingModeCalculator calculator;

    public ImageUsageExtractor(ImageBillingModeCalculator calculator) {
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
                + "像素（size）从请求体解析，作为像素档位落档依据；输入图片张数取请求侧数组长度或显式计数字段。"
                + "token 分项内嵌保留，双报模型（如 gpt-image-1 同时上报图片与 token 用量）不丢量。仅服务 IMAGE 计费模式。";
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
        // 输入图片：数组 / 计数键优先；单数字段（image / init_image / source_image）按 1 张
        long inputImages = MediaUsageReaders.imageCount(requestBody);
        if (inputImages <= 0
                && MediaUsageReaders.present(requestBody, "image", "init_image", "source_image")) {
            inputImages = 1L;
        }
        ImageBillingUsage billing = new ImageBillingUsage(
                tokens,
                ImageTokenUsage.from(UsageRawReader.readUsage(body), tokens, inputImages > 0),
                count, size.pixels(), size.width(), size.height(), inputImages);
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }
}
