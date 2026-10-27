package org.chobit.knot.adapter.usage.extractor;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.util.MapNumberUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** 时长字段名：输出侧统一用这一份，避免毫秒口径与秒口径的键表漂移 */
    private static final String[] DURATION_KEYS = {
            "duration_seconds", "audio_seconds", "video_seconds", "seconds", "duration"};

    /**
     * 时长（毫秒）：{@code usage} 别名优先、响应体顶层兜底，<b>保留小数精度</b>。
     *
     * <p>⭐ 为什么不复用 {@link #durationSeconds}：后者走 {@code firstLong}，
     * {@code 5.9} 会被 {@code Number.longValue()} 截成 {@code 5}（少收 15%），
     * {@code 0.5} 直接截成 {@code 0}（再被「至少一秒」兜成 1 秒，多收 100%）。
     * 本方法按「秒 → 毫秒」放大三位并四舍五入，精度只到毫秒为止。</p>
     *
     * <p>⚠ 键表全是秒语义（{@code *_seconds} / {@code seconds} / {@code duration}）。
     * 若某厂商把毫秒写进这些键，会得到 1000 倍用量——这类厂商须在自己的提取器里覆写
     * {@code outputMillis} 钩子，不要放宽本方法的键表。</p>
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
     *
     * <p>MiniMax 的「输入图片每张」按张计费，故这里要的是<b>元素个数</b>而不是布尔——
     * 与 {@code flag()} 的「有没有」是两种口径，不能混用。</p>
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
     *
     * <p>Boolean 直接取值；字符串按 {@code true} 解析；非 0 数字为真；
     * 其余非 null 值（文件、URL 等对象）视为「已提供」。</p>
     *
     * <p>⚠ <b>只用于布尔开关语义</b>（如 {@code generate_audio}）。判断「有没有提供某个素材」
     * 必须用 {@link #present}：本方法对字符串走 {@code Boolean.parseBoolean}，
     * 一个 URL 字符串会被判成 {@code false}。</p>
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
     *
     * <p>与 {@link #flag} 的分工是硬性的：{@code video_url} / {@code reference_url} 这类键的值是
     * URL 字符串，走 {@code flag} 会被 {@code Boolean.parseBoolean("https://…")} 判成 {@code false}，
     * 于是「有输入视频」永远识别不到。空串、空集合、空对象、显式 {@code "false"} 视为未提供。</p>
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
     * 请求体声明的分辨率档位名：{@code null} 表示没有声明。
     *
     * <p>⭐ 计费分档键要的就是这个字符串，<b>不要用宽高反推</b>：厂商的档位名不可枚举
     * （MiniMax 用 768P、Vidu 用 540P、豆包用 480p/720p/1080p，还有 2K、4K 各种写法），
     * 从宽高反推只认标准尺寸表，{@code 768P} 这类非标档会退化成裸串或空值，配了价也吃不到。
     * 厂商自己声明的写法才是最权威的键。</p>
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
     * 分辨率文本归一化：标准尺寸映射到档位名，**未识别的写法原样大写返回**。
     *
     * <p>原样返回是刻意的——档位名不可枚举（768P、540P、2K、4K 及各厂商自造名），
     * 硬塞进白名单只会让新档位配不上价。{@code auto} 之类的非档位值会落成 {@code AUTO}，
     * 配不上就自然回落默认价，不会误命中。</p>
     */
    static String normalizeResolution(Object value) {
        String text = value == null ? null : StringUtils.trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        String normalized = StringUtils.lowerCase(text)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
        return switch (normalized) {
            case "480", "480p", "854x480" -> "480P";
            case "540", "540p", "960x540" -> "540P";
            case "720", "720p", "1280x720" -> "720P";
            case "768", "768p", "1366x768", "1024x768" -> "768P";
            case "1080", "1080p", "1920x1080" -> "1080P";
            case "1440", "1440p", "2k", "qhd", "2560x1440" -> "2K";
            case "2160", "2160p", "4k", "uhd", "3840x2160" -> "4K";
            default -> StringUtils.upperCase(text);
        };
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
