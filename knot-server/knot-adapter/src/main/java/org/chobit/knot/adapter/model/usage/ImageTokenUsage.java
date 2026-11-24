package org.chobit.knot.adapter.model.usage;

import org.chobit.knot.common.util.MapNumberUtils;

import java.util.Map;

/**
 * IMAGE 模式的 token 模态拆分：文本输入、图像输入、两者的缓存命中量与图像输出。
 *
 * 上游按 {@code input_tokens_details.text_tokens} / {@code input_tokens_details.image_tokens} 上报输入模态，
 * 按 {@code output_tokens_details.image_tokens} 上报输出模态；同义别名在顶层平铺时同样读取。
 * 这些量只服务按 token 计价的图像规则，张数与像素仍是 {@link ImageBillingUsage} 的独立字段。
 *
 * 三项兜底口径（拆不出来时的归属）：输入拆分缺失 → 整笔输入按请求是否带图归入图像 / 文本；
 * 缓存拆分缺失 → 整笔缓存按同一判据归入图像 / 文本；输出拆分缺失 → 整笔输出归入图像输出。
 */
public record ImageTokenUsage(long inputText,
                              long inputImage,
                              long cachedInputText,
                              long cachedInputImage,
                              long outputImage) {

    private static final String DETAILS_INPUT = "input_tokens_details";
    private static final String DETAILS_PROMPT = "prompt_tokens_details";
    private static final String DETAILS_OUTPUT = "output_tokens_details";
    private static final String DETAILS_COMPLETION = "completion_tokens_details";

    public ImageTokenUsage {
        inputText = nonNegative(inputText);
        inputImage = nonNegative(inputImage);
        cachedInputText = nonNegative(cachedInputText);
        cachedInputImage = nonNegative(cachedInputImage);
        outputImage = nonNegative(outputImage);
    }

    public static ImageTokenUsage empty() {
        return new ImageTokenUsage(0L, 0L, 0L, 0L, 0L);
    }

    /**
     * 从 usage 原文与已解析的 token 总量拆出模态用量。
     *
     * @param usage         承载用量的 {@code usage} 对象，缺失时返回空拆分
     * @param tokens        同一次响应的 token 总量视图，提供输入 / 输出 / 缓存总量
     * @param hasInputImage 请求侧是否带输入图片，决定拆不出来时的模态归属
     */
    public static ImageTokenUsage from(Map<String, Object> usage, TokenBillingUsage tokens, boolean hasInputImage) {
        if (usage == null || usage.isEmpty()) {
            return empty();
        }
        TokenBillingUsage totals = tokens == null ? TokenBillingUsage.empty() : tokens;
        long inputText = MapNumberUtils.firstLong(usage, "input_text_tokens", "text_input_tokens")
                + nested(usage, DETAILS_INPUT, "text_tokens")
                + nested(usage, DETAILS_PROMPT, "text_tokens");
        long inputImage = MapNumberUtils.firstLong(usage, "input_image_tokens", "image_input_tokens")
                + nested(usage, DETAILS_INPUT, "image_tokens")
                + nested(usage, DETAILS_PROMPT, "image_tokens");
        if (inputText + inputImage <= 0 && totals.inputTokens() > 0) {
            if (hasInputImage) {
                inputImage = totals.inputTokens();
            } else {
                inputText = totals.inputTokens();
            }
        }
        long cachedText = MapNumberUtils.firstLong(usage, "cached_text_tokens", "cache_read_text_tokens")
                + nested(usage, DETAILS_INPUT, "cached_text_tokens")
                + nested(usage, DETAILS_PROMPT, "cached_text_tokens");
        long cachedImage = MapNumberUtils.firstLong(usage, "cached_image_tokens", "cache_read_image_tokens")
                + nested(usage, DETAILS_INPUT, "cached_image_tokens")
                + nested(usage, DETAILS_PROMPT, "cached_image_tokens");
        if (cachedText + cachedImage <= 0 && totals.cacheReadTokens() > 0) {
            if (hasInputImage) {
                cachedImage = totals.cacheReadTokens();
            } else {
                cachedText = totals.cacheReadTokens();
            }
        }
        long outputImage = MapNumberUtils.firstLong(usage, "output_image_tokens", "image_output_tokens")
                + nested(usage, DETAILS_OUTPUT, "image_tokens")
                + nested(usage, DETAILS_COMPLETION, "image_tokens");
        if (outputImage <= 0) {
            outputImage = totals.outputTokens();
        }
        return new ImageTokenUsage(inputText, inputImage, cachedText, cachedImage, outputImage);
    }

    /** 五个分量全为 0 时为空 */
    public boolean isEmpty() {
        return inputText <= 0 && inputImage <= 0 && cachedInputText <= 0 && cachedInputImage <= 0 && outputImage <= 0;
    }

    /** 逐分量取大：流式同名事件后到覆盖、缺失沿用前条，与 token 总量的合并口径一致 */
    public ImageTokenUsage mergeMax(ImageTokenUsage other) {
        if (other == null) {
            return this;
        }
        return new ImageTokenUsage(
                Math.max(inputText, other.inputText()),
                Math.max(inputImage, other.inputImage()),
                Math.max(cachedInputText, other.cachedInputText()),
                Math.max(cachedInputImage, other.cachedInputImage()),
                Math.max(outputImage, other.outputImage())
        );
    }

    private static long nested(Map<String, Object> usage, String parent, String key) {
        return MapNumberUtils.nestedLong(usage, parent, key);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }
}
