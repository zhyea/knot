package org.chobit.knot.adapter.usage.extractor;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.util.MapNumberUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * 媒体类提取器（IMAGE / VIDEO / AUDIO）共用的读取口径：响应体取用量、请求体取生成属性。
 * 包内共享，不对外暴露。
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
     * 时长（秒）：{@code usage} 里的别名字段优先，响应体顶层兜底。
     */
    static long durationSeconds(Map<String, Object> usage, Map<String, Object> body) {
        long fromUsage = MapNumberUtils.firstLong(usage,
                "duration_seconds", "audio_seconds", "video_seconds", "seconds", "duration");
        return fromUsage > 0 ? fromUsage : MapNumberUtils.firstLong(body,
                "duration_seconds", "audio_seconds", "video_seconds", "seconds", "duration");
    }

    /** 时长字段名：输出侧统一用这一份，避免毫秒口径与秒口径的键表漂移 */
    private static final String[] DURATION_KEYS = {
            "duration_seconds", "audio_seconds", "video_seconds", "seconds", "duration"};

    /**
     * 时长（毫秒）：{@code usage} 别名优先、响应体顶层兜底。
     * 键表全是秒语义（{@code *_seconds} / {@code seconds} / {@code duration}），按「秒 → 毫秒」放大三位并四舍五入。
     */
    static long durationMillis(Map<String, Object> usage, Map<String, Object> body) {
        long fromUsage = firstMillis(usage, DURATION_KEYS);
        return fromUsage > 0 ? fromUsage : firstMillis(body, DURATION_KEYS);
    }

    /** 请求侧输入视频时长（毫秒），口径同 {@link #durationMillis} */
    static long inputDurationMillis(Map<String, Object> requestBody) {
        return firstMillis(requestBody,
                "input_duration_seconds", "input_video_seconds", "input_duration", "input_video_duration",
                "source_duration", "source_video_seconds");
    }

    /** 按给定键序取第一个大于 0 的秒数，并放大成毫秒 */
    private static long firstMillis(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return 0L;
        }
        for (String key : keys) {
            long millis = secondsToMillis(source.get(key));
            if (millis > 0) {
                return millis;
            }
        }
        return 0L;
    }

    /** 秒值 → 毫秒；不可解析或非正数返回 0 */
    private static long secondsToMillis(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            BigDecimal seconds = value instanceof Number number
                    ? new BigDecimal(number.toString())
                    : new BigDecimal(String.valueOf(value).trim());
            return seconds.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /**
     * 请求侧输入图片张数：数组长度优先，其次显式计数字段。
     * 返回元素个数而非布尔，与 {@code flag()} 的「有没有」是两种口径，不能混用。
     */
    static long imageCount(Map<String, Object> requestBody) {
        if (requestBody == null) {
            return 0L;
        }
        for (String key : new String[]{"images", "input_images", "reference_images", "image_urls", "frames"}) {
            long count = countElements(requestBody.get(key));
            if (count > 0) {
                return count;
            }
        }
        return MapNumberUtils.firstLong(requestBody,
                "image_count", "input_image_count", "num_images", "n_images");
    }

    /** 数组 / 集合的元素个数；非集合返回 0 */
    private static long countElements(Object value) {
        if (value instanceof Iterable<?> iterable) {
            long count = 0L;
            for (Object ignored : iterable) {
                count++;
            }
            return count;
        }
        return 0L;
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
     * 请求侧布尔属性（是否有声音）：任一枚叶为真即为真。
     * Boolean 直接取值；字符串按 {@code true} 解析；非 0 数字为真；其余非 null 值视为「已提供」。
     * 只用于布尔开关语义（如 {@code generate_audio}）；判断「有没有提供某素材」须用 {@link #present}。
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

    /**
     * 请求侧「是否提供了某物」（存在性语义）：任一枚叶非空即为真。
     * 空串、空集合、空对象、显式 {@code "false"} 视为未提供；URL 字符串这类值须走本方法而非 {@link #flag}。
     */
    static boolean present(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (isPresent(source.get(key))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            return !trimmed.isEmpty() && !"false".equalsIgnoreCase(trimmed);
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0d;
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
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

    /** 声明分辨率的字段名，按优先级排列（与 {@link #sizeOf} 同口径） */
    private static final String[] RESOLUTION_KEYS = {"resolution", "size", "dimensions"};

    /**
     * 请求体声明的分辨率档位名，原样返回厂商写法；{@code null} 表示没有声明。
     * 它是计费分档键，不用宽高反推——档位名不可枚举。
     */
    static String resolution(Map<String, Object> requestBody) {
        if (requestBody == null) {
            return null;
        }
        for (String key : RESOLUTION_KEYS) {
            String normalized = normalizeResolution(requestBody.get(key));
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    /**
     * 分辨率文本归一化：只做去首尾空白 + 转大写两步，不做别名推断（{@code 1280x720} 不会变成 {@code 720P}）。
     * 档位清单由 DB 枚举 {@code video_resolution} 维护，不参与此处的匹配。
     */
    static String normalizeResolution(Object value) {
        String text = value == null ? null : StringUtils.trimToNull(String.valueOf(value));
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    /**
     * 请求体声明的画幅：只认语义明确的 {@code aspect_ratio}。
     * {@code size} / {@code ratio} 语义含糊，由厂商子类按实际请求参数覆写，通用键表不含。
     */
    private static final String[] ASPECT_RATIO_KEYS = {"aspect_ratio"};

    /** 请求体声明的画幅（如 {@code 16:9}）：{@code null} 表示没有声明 */
    static String aspectRatio(Map<String, Object> requestBody) {
        return requestBody == null ? null : firstText(requestBody, ASPECT_RATIO_KEYS);
    }

    /**
     * 请求体声明的模式 / 功能档（{@code wan-std} / {@code wan-pro} / {@code ONLINE} / {@code OFFLINE} 及 Kling 的动作控制），原样返回厂商写法。
     * ASCII slug 归一与 {@code MODE=} 键前缀由厂商子类 / Calculator 负责。
     */
    private static final String[] OUTPUT_MODE_KEYS = {"video_mode", "output_mode", "mode"};

    /** 请求体声明的模式 / 功能档：{@code null} 表示没有声明 */
    static String outputMode(Map<String, Object> requestBody) {
        return requestBody == null ? null : firstText(requestBody, OUTPUT_MODE_KEYS);
    }

    private static final String[] VIDEO_COUNT_KEYS = {"data", "videos"};

    /**
     * 产出条数：取 {@code data} / {@code videos} 数组长度，单个视频对象（Map）计 1 条。
     * 读不出条数时返回 0，由 {@code VideoBillingUsage#billedVideoCount()} 兜成 1 条。
     */
    static long outputVideoCount(Map<String, Object> body) {
        if (body == null) {
            return 0L;
        }
        for (String key : VIDEO_COUNT_KEYS) {
            Object value = body.get(key);
            if (value instanceof Map<?, ?>) {
                return 1L;
            }
            long count = countElements(value);
            if (count > 0) {
                return count;
            }
        }
        return 0L;
    }

    static String firstText(Map<String, Object> source, String... keys) {
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
     * 支持 {@code "1280x720"} 精确尺寸与 {@code "720p"} 预设名；无法识别（{@code auto} 等）时为 {@link #EMPTY}。
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
