package org.chobit.knot.common.constants.enums;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 视频/图片的<b>已知分辨率档位</b>：档位名在这里定义一次，归一映射、前端建议下拉、测试断言都取它。
 *
 * <p>⚠⚠ <b>本枚举不是白名单</b>（2026-10-31 robin 拍板「目录 + 建议，不限制输入」）。
 * 厂商档位客观不可枚举——MiniMax 用 {@code 768P}、Vidu 用 {@code 540P}、豆包写
 * {@code 480p/720p/1080p}，还有 {@code 2K} / {@code 4K} 与各家自造名。所以
 * {@link #canonical(String)} 对<b>未识别</b>的写法一律原样大写返回，绝不返回 null、
 * 也不拒绝保存：硬做成封闭白名单只会让「新档位配不上价」，那是把可配置能力做没了。</p>
 *
 * <p>本枚举真正解决的是<b>另一件事</b>：同一个档位被写成 {@code 720p} / {@code 720P} /
 * {@code 720} / {@code 1280x720} 四种形态时，配置键与请求键对不上，取价静默回落默认价。
 * 别名表把已知写法收敛到唯一 {@code code}，配合 {@code BillingConfig.dimensionPrice}
 * 的大小写归一，两头对齐。</p>
 */
public enum ResolutionTierEnum implements EnumOption {

    P480("480P", "480P（854×480）"),
    P540("540P", "540P（960×540）"),
    P720("720P", "720P（1280×720）"),
    P768("768P", "768P（1366×768）"),
    P1080("1080P", "1080P（1920×1080）"),
    K2("2K", "2K（2560×1440）"),
    K4("4K", "4K（3840×2160）");

    private final String code;
    private final String label;

    ResolutionTierEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 标准档位名（落进 config_json 与请求归一后的键） */
    @Override
    public String code() {
        return code;
    }

    /** 前端展示名：带像素尺寸，便于配置者一眼确认档位口径 */
    @Override
    public String label() {
        return label;
    }

    /**
     * 别名（已小写、已去分隔符）→ 档位。
     *
     * <p>⭐ 别名必须与 {@link #normalize(String)} 的清洗规则配套：这里全部写成
     * 「小写 + 无空格无下划线无连字符」，否则 {@code 854x480} 这类带字母 x 的写法永远匹配不上。</p>
     */
    private static final Map<String, ResolutionTierEnum> ALIASES = aliases();

    private static Map<String, ResolutionTierEnum> aliases() {
        Map<String, ResolutionTierEnum> map = new LinkedHashMap<>();
        register(map, P480, "480", "480p", "854x480");
        register(map, P540, "540", "540p", "960x540");
        register(map, P720, "720", "720p", "1280x720");
        register(map, P768, "768", "768p", "1366x768", "1024x768");
        register(map, P1080, "1080", "1080p", "1920x1080");
        register(map, K2, "1440", "1440p", "2k", "qhd", "2560x1440");
        register(map, K4, "2160", "2160p", "4k", "uhd", "3840x2160");
        return Map.copyOf(map);
    }

    private static void register(Map<String, ResolutionTierEnum> map, ResolutionTierEnum tier, String... keys) {
        // 档位名自身也当别名：720P 清洗后是 720p，与显式别名同键，重复 put 是幂等的
        map.put(tier.code.toLowerCase(Locale.ROOT), tier);
        for (String key : keys) {
            map.put(key, tier);
        }
    }

    /**
     * 文本清洗：小写 + 去掉空格、下划线、连字符。
     *
     * <p>刻意<b>不</b>去 {@code x}：{@code 1280x720} 的 x 是尺寸分隔符，去掉会变成
     * {@code 1280720}，与任何别名都对不上。别名表因此直接写成带 x 的形式。</p>
     */
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
    }

    /**
     * 档位归一：已知写法 → 标准 {@code code}；未识别 → <b>原样大写</b>返回。
     *
     * <p>⭐ 未识别时返回原样大写（而不是 null / 抛异常）是**刻意**的：
     * <ul>
     *   <li>厂商会随时新增档位，发版前就该能配价；</li>
     *   <li>{@code auto} 这类非档位值会落成 {@code AUTO}，配不上就按「未命中」处理，
     *       不会被误当成某个已知档位；</li>
     *   <li>真实档位名（{@code 768P}）原样大写后就是配置键的写法，配了就命中。</li>
     * </ul>
     *
     * @param text 原始分辨率文本，允许 {@code null}
     * @return 归一后的档位名；入参为 {@code null} / 空白时返回 {@code null}
     */
    public static String canonical(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResolutionTierEnum tier = ALIASES.get(normalize(trimmed));
        return tier != null ? tier.code : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * 按 code 查枚举；未命中返回 {@code null}（供「是否已知档位」这类判断，不做归一推断）。
     */
    public static ResolutionTierEnum ofCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (ResolutionTierEnum tier : values()) {
            if (tier.code.equalsIgnoreCase(trimmed)) {
                return tier;
            }
        }
        return null;
    }

    /** 全部标准档位名，按声明顺序（前端建议下拉用） */
    public static List<String> codes() {
        return Arrays.stream(values()).map(ResolutionTierEnum::code).toList();
    }
}
