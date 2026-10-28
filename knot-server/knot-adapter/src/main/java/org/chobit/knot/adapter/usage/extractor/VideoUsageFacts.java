package org.chobit.knot.adapter.usage.extractor;

import lombok.Builder;

import java.util.Map;

/**
 * 视频用量的<b>档位事实</b>：一次解析的产物，承载全部维度的取值结果。
 *
 * <p><b>为什么是一个类而不是每个档位一个方法</b>：视频用量由 10 个事实组成
 * （输出时长 / 输出条数 / 分辨率档 / 画幅 / 模式档 / 输入时长 / 输入图张数 / 有声 / 音色 / 参考视频），
 * 它们<b>来自同两份输入</b>（响应体与客户端请求体）。做成十个钩子时，同一份请求体要被十个方法
 * 各自扫一遍键表，且「视频有哪些档位」这件事散在十个签名里——新增一维要动基类、动子类、
 * 动组装点，漏掉任何一处都不报错（少一维 = 少一路钱，只在月结对账时显形）。
 * 聚成一个记录后：新增维度 = 加一个分量 + {@link #of} 里加一行解析 + 投影里加一行。</p>
 *
 * <p><b>为什么不直接用 {@code VideoBillingUsage} 当载体</b>：那是<b>计费模型</b>，
 * 它的每个字段都被「明细行 = 量 × 价」与 {@code ModelUsage} 的无损投影约束着。
 * 解析口径若反写进模型，模型就得跟着 JSON 键表走；而事实里允许存在
 * <b>只记录、不进计费</b>的观察值（日后接入的厂商会带来这种字段），
 * 那种字段不该被塞进计费模型。<b>边界：本类认识键表，模型只认识自己。</b></p>
 *
 * <p><b>厂商差异怎么改</b>：覆写 {@code AbstractVideoUsageExtractor#facts} 一处，
 * 用 {@link #toBuilder()} 只改自己那几项（其余自动保持通用解析结果）：
 * <pre>{@code
 * return VideoUsageFacts.of(body, requestBody).toBuilder()
 *         .sound(true)              // 本模型价表只有有声一档
 *         .referenceVideo(false)    // 本模型没有参考视频能力
 *         .build();
 * }</pre>
 * ⚠ 别在子类里重新 `new` 一份完整记录：那会把通用解析的结论全部丢弃，
 * 日后基类新增维度子类不会跟上（这正是「每档一个方法」时代的老毛病）。</p>
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
     * 默认「是否有声」键表（布尔开关语义，见 {@link MediaUsageReaders#flag}）。
     *
     * <p>{@code audio} 是阿里云万相的写法；与 {@code generate_audio} 等并列而非覆盖，
     * 因为同一套通用提取器要同时服务多家。</p>
     */
    private static final String[] SOUND_KEYS = {
            "generate_audio", "with_audio", "enable_audio", "audio_enabled", "sound", "audio"};

    /**
     * 默认「是否指定音色」键表（存在性语义，见 {@link MediaUsageReaders#present}）。
     *
     * <p>音色 id / 名称是字符串，必须用 {@code present}：
     * {@code flag} 会对字符串走 {@code Boolean.parseBoolean}，一律判成 {@code false}。</p>
     */
    private static final String[] VOICE_KEYS = {"voice_id", "voice", "timbre"};

    /**
     * 默认「是否有参考视频」键表（存在性语义）。
     *
     * <p>⭐ 只认视频：参考<b>图</b>不进这一维（robin 2026-10-26 拍板「有无输入视频 ≡ 有无参考视频」），
     * 图生视频的首帧图与 {@code reference_image(s)} 走输入图片张数口径。
     * 因此刻意不含 {@code reference_image} / {@code reference_images} / {@code reference} /
     * {@code reference_url} —— 后两者语义不明（可能指向图片），宁可让调用方换成语义明确的键，也不猜。</p>
     */
    private static final String[] REFERENCE_VIDEO_KEYS = {
            "input_reference", "reference_video", "reference_videos",
            "video", "video_url", "video_urls", "input_video", "input_videos"};

    /**
     * 通用解析：两份输入各走一遍，一次取齐 10 个事实。
     *
     * <p>⚠ 输出侧的 {@code usage} 子对象只在这里取一次，不再每个字段各查一遍——
     * 这是本类存在的第二个理由（避免同一份 JSON 被反复下钻）。</p>
     *
     * <p>未声明的维度给「空」而不是「猜」：字符串给 {@code null}（模型侧归一成空串，
     * 取价自然回落默认档），数值给 0（由模型侧 {@code billedVideoCount()} 之类兜底）。
     * 解析层不做兜底值——兜底是计费的语义，不是解析的。</p>
     *
     * @param body        上游响应体（已含 {@code usage} 或顶层时长/条数）
     * @param requestBody <b>客户端原始请求体</b>，不是适配器转换后的上游体
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
