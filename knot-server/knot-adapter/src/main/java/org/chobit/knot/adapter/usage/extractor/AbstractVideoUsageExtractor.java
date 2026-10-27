package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.ModelUsageNormalizer;
import org.chobit.knot.adapter.usage.UsageExtractItem;

import java.util.Map;

/**
 * VIDEO 用量提取骨架：固定「一次提取怎么组装」，把「每个字段从哪儿取」留成钩子。
 *
 * <p><b>为什么需要这层</b>：视频用量由 6 个事实组成（输出时长、输入时长、输入图张数、输出分辨率、
 * 是否有声、是否有参考视频），而各家字段名互不相同。两种极端都不可取——
 * 把厂商键表堆进单一实现，它会随接入厂商线性膨胀；让每个厂商从零实现
 * {@link UsageExtractor}，则要复制整段组装与投影逻辑（含 {@code ModelUsageNormalizer} 调用）。
 * 本类取中间：<b>通用键表是默认值，厂商差异靠覆写钩子</b>。</p>
 *
 * <p><b>后续接入某供应商</b>：{@code extends AbstractVideoUsageExtractor}，
 * 只覆写与实际协议不一致的那几个钩子，另在子类声明自己的
 * {@code code()} / {@code label()} / {@code description()} / {@code calculator()}，
 * 并在模型绑定上把 {@code extractorCode} 指向它。{@code extractUsage} 不需要重写。</p>
 *
 * <p>⚠ 子类须与本类<b>同包</b>：钩子依赖包内共享的 {@link MediaUsageReaders}（刻意不对外暴露）。</p>
 */
public abstract class AbstractVideoUsageExtractor implements UsageExtractor<VideoBillingUsage> {

    /**
     * 默认「是否有声」键表（布尔开关语义）。
     *
     * <p>{@code audio} 是阿里云万相的写法；与 {@code generate_audio} 等并列而非覆盖，
     * 因为同一套通用提取器要同时服务多家。</p>
     */
    protected static final String[] DEFAULT_SOUND_KEYS = {
            "generate_audio", "with_audio", "enable_audio", "audio_enabled", "sound", "audio"};

    /**
     * 默认「是否有参考视频」键表（存在性语义）。
     *
     * <p>⭐ 只认视频：参考<b>图</b>不进这一维（robin 2026-10-26 拍板「有无输入视频 ≡ 有无参考视频」），
     * 图生视频的首帧图与 {@code reference_image(s)} 走输入图片张数口径。
     * 因此刻意不含 {@code reference_image} / {@code reference_images} / {@code reference} /
     * {@code reference_url} —— 后两者语义不明（可能指向图片），宁可让调用方换成语义明确的键，也不猜。</p>
     */
    protected static final String[] DEFAULT_REFERENCE_VIDEO_KEYS = {
            "input_reference", "reference_video", "reference_videos",
            "video", "video_url", "video_urls", "input_video", "input_videos"};

    @Override
    public UsageExtractItem<VideoBillingUsage> empty() {
        return UsageExtractItem.of(VideoBillingUsage.empty());
    }

    @Override
    public UsageExtractItem<VideoBillingUsage> extractUsage(Map<String, Object> body,
                                                           Map<String, Object> requestBody) {
        VideoBillingUsage billing = new VideoBillingUsage(
                TokenBillingUsage.fromResponse(body),
                outputMillis(body),
                inputMillis(requestBody),
                inputImageCount(requestBody),
                outputResolution(requestBody),
                soundEnabled(requestBody),
                referenceVideoProvided(requestBody));
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }

    // ---------------- 取数钩子：默认走通用键表，厂商差异由子类覆写 ----------------

    /**
     * 输出时长（毫秒）：默认取 {@code usage} 里的别名，响应体顶层兜底。
     *
     * <p>异步任务型厂商（先回 {@code task_id}、产物在轮询结果里）需要覆写本钩子。</p>
     */
    protected long outputMillis(Map<String, Object> body) {
        return MediaUsageReaders.durationMillis(MediaUsageReaders.rawUsage(body), body);
    }

    /**
     * 输入视频时长（毫秒）：默认只读请求体的显式秒数字段。
     *
     * <p>上游不会在 usage 里回传输入时长——它是调用方声明的。厂商把输入视频放在别处
     * （如只给 URL / id，秒数在别处）时覆写本钩子。</p>
     */
    protected long inputMillis(Map<String, Object> requestBody) {
        return MediaUsageReaders.inputDurationMillis(requestBody);
    }

    /** 输入图片张数：默认数组长度优先、显式计数字段兜底（张数口径，不是「有没有」） */
    protected long inputImageCount(Map<String, Object> requestBody) {
        return MediaUsageReaders.imageCount(requestBody);
    }

    /**
     * 输出分辨率档位名：默认取请求体声明的写法（{@code 768P} / {@code 1080p} / {@code 2K}…），
     * 归一化后原样交给计费当分档键。
     *
     * <p>⚠ 不要改成「由宽高反推」：厂商档位名不可枚举，反推只认标准尺寸表，
     * 非标档（768P、540P）会退化成裸串或空值，配了价也吃不到。没声明时返回空串，
     * 取价自然回落默认档。</p>
     */
    protected String outputResolution(Map<String, Object> requestBody) {
        String resolution = MediaUsageReaders.resolution(requestBody);
        return resolution == null ? "" : resolution;
    }

    /** 是否有声：默认走 {@link #DEFAULT_SOUND_KEYS}（{@link MediaUsageReaders#flag} 布尔开关语义） */
    protected boolean soundEnabled(Map<String, Object> requestBody) {
        return MediaUsageReaders.flag(requestBody, DEFAULT_SOUND_KEYS);
    }

    /**
     * 是否有参考 / 输入视频：默认走 {@link #DEFAULT_REFERENCE_VIDEO_KEYS}。
     *
     * <p>⚠ 必须用 {@link MediaUsageReaders#present}（存在性）而不是 {@code flag}（布尔开关）：
     * 这些键的值通常是 URL 字符串，{@code flag} 走 {@code Boolean.parseBoolean} 会把它判成
     * {@code false}，于是一律识别不到。</p>
     */
    protected boolean referenceVideoProvided(Map<String, Object> requestBody) {
        return MediaUsageReaders.present(requestBody, DEFAULT_REFERENCE_VIDEO_KEYS);
    }
}
