package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.ModelUsageNormalizer;
import org.chobit.knot.adapter.usage.UsageExtractItem;

import java.util.Map;

/**
 * VIDEO 用量提取骨架：固定「一次提取怎么组装」，把「档位事实从哪儿取」留成<b>一个</b>钩子。
 *
 * <p><b>为什么需要这层</b>：视频用量由 10 个事实组成，而各家字段名互不相同。两种极端都不可取——
 * 把厂商键表堆进单一实现，它会随接入厂商线性膨胀；让每个厂商从零实现
 * {@link UsageExtractor}，则要复制整段组装与投影逻辑（含 {@code ModelUsageNormalizer} 调用）。
 * 本类取中间：<b>通用解析是默认值（{@link VideoUsageFacts#of}），厂商差异靠覆写 {@link #facts} 一处</b>。</p>
 *
 * <p><b>为什么取数只留一个钩子</b>：十个钩子看着「各管一维」，实际是同一份请求体被扫十遍，
 * 且「视频有哪些档位」散落在十个签名里——加一维要动基类、动每个子类、动组装点，
 * 漏任一处的表现是<b>少收一路钱且无报错</b>。聚成 {@link VideoUsageFacts} 后加一维只动两处
 * （记录分量 + 解析行）。详见该类 javadoc。</p>
 *
 * <p><b>后续接入某供应商</b>：{@code extends AbstractVideoUsageExtractor}，
 * 只覆写 {@link #facts}（用 {@code toBuilder()} 改差异项），另在子类声明自己的
 * {@code code()} / {@code label()} / {@code description()} / {@code calculator()}，
 * 并在模型绑定上把 {@code extractorCode} 指向它。{@code extractUsage} 不需要重写。</p>
 *
 * <p>⚠ 子类须与本类<b>同包</b>：解析原语 {@link MediaUsageReaders} 刻意不对外暴露。</p>
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
     * ⭐ 唯一的取数入口：一次拿到全部档位事实。
     *
     * <p>默认就是通用键表（{@link VideoUsageFacts#of}）。厂商差异用
     * {@code VideoUsageFacts.of(body, requestBody).toBuilder()...build()} 改自己那几项，
     * 其余保持通用结论——<b>不要</b>重新 {@code new} 一份完整记录，那等于把通用解析丢掉。</p>
     */
    protected VideoUsageFacts facts(Map<String, Object> body, Map<String, Object> requestBody) {
        return VideoUsageFacts.of(body, requestBody);
    }

    /**
     * 事实 → 计费模型的一次投影。
     *
     * <p>显式逐字段写，不反射不拷贝：这里是唯一能一眼看出「哪些事实真的进了计费」的地方。
     * {@code tokens} 不属档位事实（是上游可能同时上报的 token 用量），故由本方法单独取。</p>
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
