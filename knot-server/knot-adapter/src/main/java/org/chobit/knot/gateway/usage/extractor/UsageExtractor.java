package org.chobit.knot.gateway.usage.extractor;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.usage.UsageExtractItem;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.util.JsonKit;

import java.util.Map;
import java.util.Set;

/**
 * 上游响应用量提取器：按协议把响应体（连同本次请求体）一次解析成双视图
 * {@link UsageExtractItem}——计费输入 {@code billing} 与对外统一用量 {@code usage}。
 *
 * <p>泛型 {@code T} 与计费模式一一对应：DEFAULT/ANTHROPIC 产出 {@code TokenBillingUsage}，
 * IMAGE/VIDEO/AUDIO 产出各自的多模态用量记录。计算器按 sealed 类型分摄取量，
 * 「提取量」与「怎么计价」在类型上就分清了。</p>
 *
 * <p>{@code requestBody} 是像素（{@code size}）、是否有声音（{@code generate_audio}）、
 * 是否有参考（{@code input_reference}）等请求侧计费属性的来源——这些参数上游响应不回传，
 * 只有请求体知道。</p>
 *
 * @param <T> 该提取器产出的计费输入类型
 */
public interface UsageExtractor<T extends BillingUsage> {

    String code();

    String label();

    /**
     * 该提取器作用的自然语言描述：面向运维与模型配置人员，说明它从哪种上游响应里取什么量。
     *
     * <p>与 {@link #label()} 的分工：label 是下拉里的短名（恒简短），description 是提取器
     * 说明页上的完整阐述——覆盖协议口径、取量字段、与计费模式的对应关系。纯展示，不参与逻辑。</p>
     */
    String description();

    default boolean streamSupported() {
        return true;
    }

    /**
     * 该提取器是否允许「提取结果为空」——即使上游 {@code usage} 里存在正数。
     *
     * <p>默认 {@code false}：上游回了非零用量却提取不出来，视为口径错配并直接失败。
     * 少数供应商的 {@code usage} 会带本提取器不消费的字段（如 {@code reasoning_tokens}、
     * 媒体时长），此时提取出全零是<b>合法</b>的，必须由该提取器显式覆写为 {@code true} 声明。</p>
     *
     * <p>这是<b>提取器边界的一次性决策</b>，不是运行期启发式：能不能为空由绑定在本模型上的
     * 提取器决定，注册表不再跨协议猜测「有正数就等于该提取器读得出来」。</p>
     */
    default boolean allowsEmptyUsage() {
        return false;
    }

    /**
     * 该解析方案支持的计费模式：前端下拉按已绑计费规则的 {@code billingMode} 收敛候选，缩小筛选范围。
     *
     * <p>与泛型 {@code T} 一一对应：token 记录 ↔ TOKEN，各媒体记录 ↔ 各自的模式；
     * DEFAULT 是通用兜底（Simple 计算器按规则模式分摄取量），声明全部模式。</p>
     */
    Set<BillingModeEnum> billingModes();

    BillingModeCalculator calculator();

    /** 空用量：SSE 逐事件合并的起点，也是无用量响应的兜底 */
    UsageExtractItem<T> empty();

    /**
     * 从上游响应体一次提取双视图用量。
     *
     * @param body        上游响应体（整包或单个 SSE 事件）
     * @param requestBody 本次请求体，可为 {@code null}
     */
    UsageExtractItem<T> extractUsage(Map<String, Object> body, Map<String, Object> requestBody);

    /** 无请求体场景的便捷重载（如单测） */
    default UsageExtractItem<T> extractUsage(Map<String, Object> body) {
        return extractUsage(body, Map.of());
    }

    default UsageExtractItem<T> extractUsageBody(String responseBody, Map<String, Object> requestBody) {
        if (StringUtils.isBlank(responseBody)) {
            return empty();
        }
        if (isEventStream(responseBody)) {
            return extractEventStreamUsage(responseBody, requestBody);
        }
        Map<String, Object> body = readMap(responseBody);
        return body == null || body.isEmpty() ? empty() : extractUsage(body, requestBody);
    }

    default UsageExtractItem<T> extractEventStreamUsage(String responseBody, Map<String, Object> requestBody) {
        UsageExtractItem<T> usage = empty();
        for (String line : responseBody.split("\\R")) {
            String trimmed = StringUtils.trim(line);
            if (!StringUtils.startsWith(trimmed, "data:")) {
                continue;
            }
            String data = StringUtils.trim(trimmed.substring("data:".length()));
            if (StringUtils.isEmpty(data) || "[DONE]".equals(data)) {
                continue;
            }
            Map<String, Object> event = readMap(data);
            if (event == null || event.isEmpty()) {
                continue;
            }
            usage = usage.mergeMax(extractUsage(event, requestBody));
        }
        return usage;
    }

    private static boolean isEventStream(String value) {
        return value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    private static Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
    }
}
