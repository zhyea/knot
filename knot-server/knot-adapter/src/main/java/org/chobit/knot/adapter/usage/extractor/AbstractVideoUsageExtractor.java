package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.ModelUsageNormalizer;
import org.chobit.knot.adapter.usage.UsageExtractItem;

import java.util.Map;

/**
 * VIDEO 用量提取骨架：固定「一次提取怎么组装」，把「档位事实从哪儿取」留成唯一钩子 {@link #facts}。
 * 接入新供应商：{@code extends AbstractVideoUsageExtractor}，覆写 {@link #facts}（用 {@code toBuilder()} 改差异项），
 * 并声明 {@code code()} / {@code label()} / {@code description()} / {@code calculator()}，在模型绑定上把 {@code extractorCode} 指向它；{@code extractUsage} 不需重写。
 * 子类须与本类同包：解析原语 {@link MediaUsageReaders} 不对外暴露。
 */
public abstract class AbstractVideoUsageExtractor implements UsageExtractor<VideoBillingUsage> {

    @Override
    public UsageExtractItem<VideoBillingUsage> empty() {
        return UsageExtractItem.of(VideoBillingUsage.empty());
    }

    @Override
    public UsageExtractItem<VideoBillingUsage> extractUsage(Map<String, Object> body,
                                                           Map<String, Object> requestBody) {
        VideoBillingUsage billing = toBilling(facts(body, requestBody), body);
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }

    /**
     * 唯一的取数入口：一次拿到全部档位事实，默认走通用键表 {@link VideoUsageFacts#of}。
     * 厂商差异用 {@code VideoUsageFacts.of(body, requestBody).toBuilder()...build()} 只改差异项，其余保持通用结论。
     */
    protected VideoUsageFacts facts(Map<String, Object> body, Map<String, Object> requestBody) {
        return VideoUsageFacts.of(body, requestBody);
    }

    /**
     * 事实 → 计费模型的一次投影，逐字段显式写出（不反射、不拷贝）。
     * {@code tokens} 不属于档位事实，由本方法单独取。
     */
    private static VideoBillingUsage toBilling(VideoUsageFacts facts, Map<String, Object> body) {
        return VideoBillingUsage.builder()
                .tokens(TokenBillingUsage.fromResponse(body))
                .outputVideoMillis(facts.outputVideoMillis())
                .outputResolution(facts.outputResolution())
                .outputAspectRatio(facts.outputAspectRatio())
                .outputMode(facts.outputMode())
                .outputVideoCount(facts.outputVideoCount())
                .inputVideoMillis(facts.inputVideoMillis())
                .inputImageCount(facts.inputImageCount())
                .sound(facts.sound())
                .voiceSpecified(facts.voiceSpecified())
                .referenceVideo(facts.referenceVideo())
                .build();
    }
}
