package org.chobit.knot.adapter.support.kling;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kling（可灵）视频生成的请求体转换：把网关对外契约 {@code VideoGenerationRequest}
 * （{@code prompt / image / duration / size / quality / n / user}）翻译成 Kling 形态。
 *
 * <p><b>这里放逻辑、适配器只留身份</b>：转换要处理分支（文生 / 图生）与校验（分辨率白名单、
 * {@code n>1}），塞进 {@code @Component} 的适配器类里会让「点了没反应」的排查面变大。
 * 既有 {@code AwsBedrockConverseSupport} 同构。</p>
 *
 * <p>⭐ <b>不做宽高反推</b>：{@code size} 是客户端声明的<b>档位名</b>（{@code 720p} / {@code 1080p}），
 * 由厂商约定而非像素计算得来（{@code VideoBillingUsage} javadoc 是这条红线的权威表述）。
 * 反推会把 768P 这类非标档折成一个裸串，到了计价侧就再也配不上价。</p>
 */
public final class KlingVideoSupport {

    /**
     * 该模型支持的分辨率取值（与官方价表的档位一一对应，小写原样下发）。
     *
     * <p><b>为什么必须白名单</b>：通用的 {@code normalizeResolution} 对未识别写法原样大写返回
     * （{@code 2k → 2K}），配不上价就静默回落到 {@code defaultUnitPrice}，
     * <b>全程无日志</b>——一次的错价要等月末对账才知道。这里的档位可枚举，
     * 所以在入口明确拒绝，把「静默错价」换成「当场失败」。</p>
     */
    private static final Map<String, String> RESOLUTIONS = Map.of("720P", "720p", "1080P", "1080p");

    /** 图生视频的内容段类型：Kling 只支持首帧（官方明确不支持尾帧） */
    private static final String FIRST_FRAME = "first_frame";

    private static final String PROMPT = "prompt";

    private KlingVideoSupport() {
    }

    /**
     * 转换请求体。
     *
     * @param context 上游请求上下文；取 {@code requestBody()}（客户端原始体）与 {@code requestId()}
     * @return Kling 形态的请求体
     * @throws BusinessException 分辨率不在支持集合内、{@code n>1}、既无 prompt 也无首帧图
     */
    public static Map<String, Object> buildRequestBody(UpstreamRequestContext context) {
        Map<String, Object> source = context == null ? Map.of() : context.requestBody();
        rejectBatch(source);
        Map<String, Object> body = new LinkedHashMap<>();

        String prompt = StringUtils.trimToNull(text(source.get(PROMPT)));
        String image = imageUrl(source.get("image"));
        if (image != null) {
            // 图生视频：prompt 与首帧都进 contents，顶层不再放 prompt
            List<Map<String, Object>> contents = new ArrayList<>();
            if (prompt != null) {
                contents.add(Map.of("type", PROMPT, "text", prompt));
            }
            contents.add(Map.of("type", FIRST_FRAME, "url", image));
            body.put("contents", contents);
        } else {
            if (prompt == null) {
                // 上游会以 400 回绝同样的句子，但那时已经是一次出站调用；在入口拦，错误也更可读
                throw bad("prompt is required when image is absent");
            }
            body.put(PROMPT, prompt);
        }

        Map<String, Object> settings = new LinkedHashMap<>();
        String resolution = resolution(source.get("size"));
        if (resolution != null) {
            settings.put("resolution", resolution);
        }
        Integer duration = duration(source.get("duration"));
        if (duration != null) {
            settings.put("duration", duration);
        }
        if (!settings.isEmpty()) {
            body.put("settings", settings);
        }

        String requestId = context == null ? null : context.requestId();
        if (StringUtils.isNotBlank(requestId)) {
            // ⭐ 为什么是 requestId 而不是任务句柄：任务句柄要等上游返回 task_id 之后才生成，
            // 而本方法在<b>出站之前</b>执行——那时句柄还不存在。requestId 同样是每请求唯一的 32 位 hex，
            // 反查能力完全一致（GET /tasks?external_task_ids=<requestId>）。
            // ⚠ 与 AsyncTaskSupport 的约定：异步层回填 external_task_id 时取同一个值，
            // 两侧注释互相指向，改一处必须改另一处。
            body.put("options", Map.of("external_task_id", requestId.trim()));
        }
        return body;
    }

    /**
     * 单次只能出一个视频：{@code n>1} 明确拒绝。
     *
     * <p>静默忽略等于「收一次钱交一份货」，比报错糟糕得多——客户端会以为 n 生效了。</p>
     */
    private static void rejectBatch(Map<String, Object> source) {
        Object n = source == null ? null : source.get("n");
        if (n instanceof Number number && number.intValue() > 1) {
            throw bad("n>1 is not supported: one request creates exactly one video");
        }
    }

    private static String resolution(Object value) {
        String raw = StringUtils.trimToNull(text(value));
        if (raw == null) {
            return null;
        }
        String canonical = RESOLUTIONS.get(raw.toUpperCase());
        if (canonical == null) {
            throw bad("unsupported size for this model: " + raw + ", expected one of " + RESOLUTIONS.keySet());
        }
        return canonical;
    }

    private static Integer duration(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String raw = StringUtils.trimToNull(text(value));
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw bad("duration must be an integer number of seconds: " + raw);
        }
    }

    /**
     * 首帧图地址：字符串直接取；数组取第一个字符串元素（兼容客户端把单个值写成数组）。
     *
     * <p>取不到就返回 {@code null} —— 「没有图」是合法分支（文生视频），不是错误。</p>
     */
    private static String imageUrl(Object value) {
        if (value instanceof String raw) {
            return StringUtils.trimToNull(raw);
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String raw) {
                    String trimmed = StringUtils.trimToNull(raw);
                    if (trimmed != null) {
                        return trimmed;
                    }
                }
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BusinessException bad(String message) {
        // 400 + stage=request_parse：本类在 knot-adapter，不能抛网关私有异常家族（红线），
        // 走 knot-common 的跨模块 400 通道，由 GatewayExceptionHandler 统一翻译
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
