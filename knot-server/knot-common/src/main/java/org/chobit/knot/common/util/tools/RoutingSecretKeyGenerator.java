package org.chobit.knot.common.util.tools;

import java.util.UUID;

/**
 * 消费者 API Key 生成器，格式为 {@code sk-} + UUID 去横线。
 */
public final class RoutingSecretKeyGenerator {

    private static final String PREFIX = "sk-";

    /**
     * 单把 key 的长度上限。生成器固定产出 35 字符（{@code sk-} + 32 hex），这里留足余量，
     * 只为挡住「异常超长输入」——每次换一把超长 key 就能在缓存 / 日志里制造无界条目。
     *
     * <p>⚠ 刻意<b>不</b>校验字符集与定长：库内存量 key 里仍有手工造的 {@code sk-de…-0N}
     * （2026-11-11 普查 4 行全部如此），严格化会立刻打断既有调用方；等生产库普查完再收紧。</p>
     */
    private static final int MAX_LENGTH = 128;

    private RoutingSecretKeyGenerator() {
    }

    /**
     * Generates a new consumer API key.
     */
    public static String generate() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 入站校验：只认 {@code sk-} 前缀 + 不超过 {@link #MAX_LENGTH} 字符。
     *
     * <p>刻意<b>不</b>校验字符集与定长（原因见 {@link #MAX_LENGTH}）；这条口径由
     * {@code RoutingSecretKeyGeneratorTest} 钉住，要收紧先改那个文件的断言。</p>
     */
    public static boolean isRoutingSecretKey(String key) {
        return key != null
                && key.length() <= MAX_LENGTH
                && key.startsWith(PREFIX)
                && key.length() > PREFIX.length();
    }
}
