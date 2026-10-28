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
 * 可灵（Kling）视频用量提取器：覆写通用键表对不上的 4 个钩子。
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
     * 输出时长：{@code data[0].outputs[]} 里第一个 {@code type=video} 的 {@code duration}（字符串秒）。
     *
     * <p>⚠ 绝不回退到通用键表：那条键表是「顶层 / usage」语义，Kling 的时长在两层数组里。
     * 更要紧的是不能<b>兜底成 1 秒</b>——没有产物时长就是 0，由异步层判定为失败；
     * 让「没生成出来」表现为「少收 1 秒」是资损且事后查不出来。</p>
     */
    @Override
    protected long outputMillis(Map<String, Object> body) {
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
     * 恒 {@code true}：官方价表只有「有声」一档，请求体里也没有音频开关。
     *
     * <p>写成模型事实而不是读请求体，是因为读请求体时缺字段会退化成 {@code false}，
     * 而 {@code false} 会去查另一组键（{@code 720P} 而非 {@code 720P:AUDIO}）——
     * 本模型两者都指向同一档价，但日后出现无声档时就是另一路钱。</p>
     */
    @Override
    protected boolean soundEnabled(Map<String, Object> requestBody) {
        return true;
    }

    /**
     * 恒 {@code false}：3.0 Turbo 没有参考视频能力，图生视频的首帧是<b>图片</b>不是视频
     * （红线：参考图走图片张数口径，不计入「有无输入视频」这一维）。
     *
     * <p>显式关掉还挡住另一回事：通用 {@code DEFAULT_REFERENCE_VIDEO_KEYS} 里有 {@code video / video_url}，
     * 日后客户端多传一个同名字段就会把这一维点亮，凭空多出一路钱。</p>
     */
    @Override
    protected boolean referenceVideoProvided(Map<String, Object> requestBody) {
        return false;
    }

    /**
     * 输入图片张数：请求体 {@code image} 非空记 1（Kling 只吃一张首帧）。
     *
     * <p>只是<b>事实记录</b>（进 {@code ModelUsage.input.imageCount}），
     * 规则侧不配 {@code inputImagePerImage} 就不会产生任何金额分量。</p>
     */
    @Override
    protected long inputImageCount(Map<String, Object> requestBody) {
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
