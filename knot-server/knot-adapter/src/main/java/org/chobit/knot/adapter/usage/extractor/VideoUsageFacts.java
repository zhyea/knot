package org.chobit.knot.adapter.usage.extractor;

import lombok.Builder;

import java.util.Map;

/**
 * 视频用量的档位事实：一次解析的产物，承载输出时长 / 输出条数 / 分辨率档 / 画幅 / 模式档 / 输入时长 / 输入图张数 / 有声 / 音色 / 参考视频十个维度。
 * 十个维度来自同两份输入（响应体与客户端请求体）；厂商差异在 {@code AbstractVideoUsageExtractor#facts} 覆写一处，用 {@link #toBuilder()} 只改自己那几项。
 * 本类认识键表，{@code VideoBillingUsage} 只认识自己；别在子类里重新 {@code new} 一份完整记录。
 */
@Builder(toBuilder = true)
public record VideoUsageFacts(long outputVideoMillis,
                              String outputResolution,
                              String outputAspectRatio,
                              String outputMode,
                              long outputVideoCount,
                              long inputVideoMillis,
                              long inputImageCount,
                              boolean sound,
                              boolean voiceSpecified,
                              boolean referenceVideo) {

    /**
     * 默认「是否有声」键表（布尔开关语义，见 {@link MediaUsageReaders#flag}），含 {@code audio} 等各家写法。
     */
    private static final String[] SOUND_KEYS = {
            "generate_audio", "with_audio", "enable_audio", "audio_enabled", "sound", "audio"};

    /**
     * 默认「是否指定音色」键表（存在性语义，见 {@link MediaUsageReaders#present}）。
     * 音色 id / 名称是字符串，须用 {@code present} 而非 {@code flag}。
     */
    private static final String[] VOICE_KEYS = {"voice_id", "voice", "timbre"};

    /**
     * 默认「是否有参考视频」键表（存在性语义）：只认视频，参考图不进这一维。
     * 图生视频的首帧图与 {@code reference_image(s)} 走输入图片张数口径；键表不含 {@code reference} / {@code reference_url} 这类语义不明的键。
     */
    private static final String[] REFERENCE_VIDEO_KEYS = {
            "input_reference", "reference_video", "reference_videos",
            "video", "video_url", "video_urls", "input_video", "input_videos"};

    /**
     * 通用解析：两份输入各走一遍，一次取齐 10 个事实。
     * 未声明的维度给「空」而不是「猜」：字符串给 {@code null}，数值给 0，解析层不做兜底值。
     *
     * @param body        上游响应体（已含 {@code usage} 或顶层时长/条数）
     * @param requestBody 客户端原始请求体，不是适配器转换后的上游体
     */
    public static VideoUsageFacts of(Map<String, Object> body, Map<String, Object> requestBody) {
        Map<String, Object> usage = MediaUsageReaders.rawUsage(body);
        return new VideoUsageFacts(
                MediaUsageReaders.durationMillis(usage, body),
                MediaUsageReaders.resolution(requestBody),
                MediaUsageReaders.aspectRatio(requestBody),
                MediaUsageReaders.outputMode(requestBody),
                MediaUsageReaders.outputVideoCount(body),
                MediaUsageReaders.inputDurationMillis(requestBody),
                MediaUsageReaders.imageCount(requestBody),
                MediaUsageReaders.flag(requestBody, SOUND_KEYS),
                MediaUsageReaders.present(requestBody, VOICE_KEYS),
                MediaUsageReaders.present(requestBody, REFERENCE_VIDEO_KEYS));
    }
}
