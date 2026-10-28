package org.chobit.knot.adapter.usage.extractor;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.VideoBillingModeCalculator;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可灵（Kling）视频用量提取器：只覆写 {@link #facts} 一处，用 {@code toBuilder()} 改 4 项。
 *
 * <p>改的 4 项分两类，都不是「键名对不上」那种小差异：</p>
 * <ol>
 *   <li><b>输出时长在两层数组里</b>（{@code data[0].outputs[]} 中 {@code type=video} 的
 *       {@code duration}，官方给字符串秒）——通用键表只扫 {@code usage} 与顶层，取不到；</li>
 *   <li><b>另外三项是模型事实，不是解析结果</b>：有声恒 {@code true}（价表只有有声一档）、
 *       参考视频恒 {@code false}（没有该能力，首帧是图片不是视频）、输入图按 {@code image} 记 1 张。
 *       写成常量而不是读请求体，是为了让「本模型的事实」只有一处定义。</li>
 * </ol>
 *
 * <p>⭐ <b>两个入参不是一个东西</b>：响应体是 Kling 的查询响应，请求体则是<b>客户端原始体</b>
 * （{@code prompt / image / duration / size}），不是 Kling 的 {@code settings.resolution} —— 提取器永远看客户端契约这一份
 * （{@code UsageExtractorRegistry#extract} 传的是 {@code context.requestBody()}，
 * 适配器转换后的上游体只用于发请求，从不回灌）。按上游键写会一个都命不中。</p>
 *
 * <p>为什么是子类而不是往 {@code VideoUsageExtractor} 里加键：那样通用提取器会随接入厂商线性膨胀，
 * 且「某个键属于哪家」再也说不清。厂商差异一律独立成类。</p>
 */
@Component
public class KlingVideoUsageExtractor extends AbstractVideoUsageExtractor {

    public static final String CODE = "KLING_VIDEO";

    private final VideoBillingModeCalculator calculator;

    public KlingVideoUsageExtractor(VideoBillingModeCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Kling Video Usage Extractor";
    }

    @Override
    public String description() {
        return "可灵视频生成提取器：输出时长取查询响应 data[0].outputs[] 中 type=video 的 duration"
                + "（官方给的是【字符串】秒，通用键表只扫 usage 与顶层，取不到故单独覆写）；"
                + "分辨率从请求体 size 取档位名；有声恒为 true、参考视频恒为 false —— 这是模型事实："
                + "3.0 Turbo 价表只有有声一档，且不支持参考视频（图生视频的首帧是图片不是视频）；"
                + "请求体带 image 时记 1 张输入图，仅作事实展示，规则不配单价即不产生金额分量。"
                + "仅服务 VIDEO 计费模式。";
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        return EnumSet.of(BillingModeEnum.VIDEO);
    }

    /**
     * 一次取齐：通用解析打底，只改 4 项。
     *
     * <p>用 {@code toBuilder()} 而不是重新 {@code new VideoUsageFacts(...)}：后者会把通用解析的
     * 分辨率 / 画幅 / 模式 / 条数 / 输入时长全部丢弃，且日后基类新增维度本类不会跟上。</p>
     */
    @Override
    protected VideoUsageFacts facts(Map<String, Object> body, Map<String, Object> requestBody) {
        return VideoUsageFacts.of(body, requestBody).toBuilder()
                .outputVideoMillis(productMillis(body))
                .inputImageCount(firstFrameCount(requestBody))
                .sound(true)
                .referenceVideo(false)
                .build();
    }

    /**
     * 产物时长：{@code data[0].outputs[]} 里第一个 {@code type=video} 的 {@code duration}（字符串秒）。
     *
     * <p>⚠ 绝不回退到通用键表：那条键表是「顶层 / usage」语义，Kling 的时长在两层数组里。
     * 更要紧的是不能<b>兜底成 1 秒</b>——没有产物时长就是 0，由异步层判定为失败；
     * 让「没生成出来」表现为「少收 1 秒」是资损且事后查不出来。</p>
     */
    private static long productMillis(Map<String, Object> body) {
        Map<String, Object> task = firstTask(body == null ? null : body.get("data"));
        if (task == null) {
            return 0L;
        }
        for (Map<String, Object> output : maps(task.get("outputs"))) {
            String type = text(output.get("type"));
            // 未标 type 的产物按视频处理：该位置只可能是视频
            if (type != null && !type.isEmpty() && !"video".equalsIgnoreCase(type)) {
                continue;
            }
            Object duration = output.get("duration");
            if (duration == null) {
                continue;
            }
            // 借唯一的秒→毫秒换算实现，不另写一份 BigDecimal 公式（两份口径必然漂移）：
            // usage 段传 null，让它落到「响应体顶层」那一侧的键表上。
            return MediaUsageReaders.durationMillis(null, Map.of("duration", duration));
        }
        return 0L;
    }

    /**
     * 输入图片张数：请求体 {@code image} 非空记 1（Kling 只吃一张首帧）。
     *
     * <p>只是<b>事实记录</b>（进 {@code ModelUsage.input.imageCount}），
     * 规则侧不配 {@code inputImagePerImage} 就不会产生任何金额分量。</p>
     */
    private static long firstFrameCount(Map<String, Object> requestBody) {
        if (requestBody == null) {
            return 0L;
        }
        Object image = requestBody.get("image");
        if (image instanceof CharSequence raw && StringUtils.isNotBlank(raw)) {
            return 1L;
        }
        if (image instanceof Iterable<?> items) {
            // 兼容客户端把单个值写成数组：Kling 只吃一张首帧，任一项非空即记 1
            for (Object item : items) {
                if (item instanceof CharSequence raw && StringUtils.isNotBlank(raw)) {
                    return 1L;
                }
            }
        }
        return 0L;
    }

    /** 取 {@code data} 的第 0 项：Kling 查询响应里 {@code data} 是长度为 1 的数组 */
    private static Map<String, Object> firstTask(Object data) {
        if (data instanceof Map<?, ?> raw) {
            return cast(raw);
        }
        List<Map<String, Object>> tasks = maps(data);
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    /** 取集合里的对象节点；非集合返回空列表（不是 null，省掉调用方的判空） */
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Iterable<?> items)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> raw) {
                result.add(cast(raw));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
