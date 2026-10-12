package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.util.MapNumberUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 媒体类提取器（IMAGE / VIDEO / AUDIO）共用的读取口径：响应体取用量、请求体取生成属性。
 *
 * <p>包内共享，不对外暴露。</p>
 */
final class MediaUsageReaders {

    private MediaUsageReaders() {
    }

    /** 响应体里的 {@code usage} 子对象，缺失或非对象时返回 {@code null} */
    @SuppressWarnings("unchecked")
    static Map<String, Object> rawUsage(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object usage = body.get("usage");
        return usage instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /**
     * 时长（秒）：{@code usage} 里的别名字段优先，响应体兜底。
     *
     * <p>两级兜底与旧口径一致：上游把秒数写进 usage 时以 usage 为准，
     * 只在响应体顶层上报时（如视频任务对象、转写结果）才取响应体。</p>
     */
    static long durationSeconds(Map<String, Object> usage, Map<String, Object> body) {
        long fromUsage = MapNumberUtils.firstLong(usage,
                "duration_seconds", "audio_seconds", "video_seconds", "seconds", "duration");
        return fromUsage > 0 ? fromUsage : MapNumberUtils.firstLong(body,
                "duration_seconds", "audio_seconds", "video_seconds", "seconds", "duration");
    }

    /** 响应体 {@code data} 数组长度：图像类接口用它表示产出张数 */
    static long countData(Map<String, Object> body) {
        if (body == null) {
            return 0L;
        }
        Object data = body.get("data");
        if (data instanceof Iterable<?> iterable) {
            long count = 0L;
            for (Object ignored : iterable) {
                count++;
            }
            return count;
        }
        return 0L;
    }

    /**
     * 请求侧布尔属性（是否有声音 / 是否有参考）：任一枚叶为真即为真。
     *
     * <p>Boolean 直接取值；字符串按 {@code true} 解析；非 0 数字为真；
     * 其余非 null 值（文件、URL 等对象）视为「已提供」。</p>
     */
    static boolean flag(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (truthy(source.get(key))) {
                return true;
            }
        }
        return false;
    }

    /** 请求体里的尺寸：{@code size} / {@code resolution} 文本，或显式 {@code width} / {@code height} */
    static MediaSize sizeOf(Map<String, Object> requestBody) {
        if (requestBody == null) {
            return MediaSize.EMPTY;
        }
        MediaSize parsed = MediaSize.parse(firstText(requestBody, "size", "resolution", "dimensions"));
        if (parsed != null) {
            return parsed;
        }
        return MediaSize.ofDimensions(
                MapNumberUtils.firstLong(requestBody, "width"),
                MapNumberUtils.firstLong(requestBody, "height"));
    }

    private static String firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0d;
        }
        return true;
    }

    /**
     * 请求侧尺寸的结构化形态：宽 / 高 / 像素（宽 × 高）。
     *
     * <p>支持两类写法：{@code "1280x720"} 精确尺寸与 {@code "720p"} 预设名；
     * 无法识别（{@code auto} 等）时为 {@link #EMPTY}，由计费规则侧决定回落价格。</p>
     */
    record MediaSize(long width, long height, long pixels) {

        static final MediaSize EMPTY = new MediaSize(0L, 0L, 0L);

        static MediaSize ofDimensions(long width, long height) {
            if (width <= 0 || height <= 0) {
                return EMPTY;
            }
            return new MediaSize(width, height, width * height);
        }

        /** 解析尺寸文本；无法识别返回 {@code null}（交由调用方尝试其它来源） */
        static MediaSize parse(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            String normalized = text.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
            int separator = separatorIndexOf(normalized);
            if (separator > 0) {
                Long width = parseDimension(normalized.substring(0, separator));
                Long height = parseDimension(normalized.substring(separator + 1));
                if (width != null && height != null) {
                    return ofDimensions(width, height);
                }
                return null;
            }
            return preset(normalized);
        }

        private static int separatorIndexOf(String normalized) {
            for (int index = 0; index < normalized.length(); index++) {
                char ch = normalized.charAt(index);
                if (ch == 'x' || ch == '*' || ch == '×') {
                    return index;
                }
            }
            return -1;
        }

        private static Long parseDimension(String text) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static MediaSize preset(String normalized) {
            return switch (normalized) {
                case "480p", "sd" -> ofDimensions(854, 480);
                case "720p", "720", "hd" -> ofDimensions(1280, 720);
                case "1080p", "1080", "fullhd", "fhd" -> ofDimensions(1920, 1080);
                case "1440p", "2k", "qhd" -> ofDimensions(2560, 1440);
                case "2160p", "4k", "uhd" -> ofDimensions(3840, 2160);
                default -> null;
            };
        }
    }
}
