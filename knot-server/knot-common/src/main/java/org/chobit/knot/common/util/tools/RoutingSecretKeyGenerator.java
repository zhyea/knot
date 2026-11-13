package org.chobit.knot.common.util.tools;

import java.util.UUID;

/**
 * 消费者 API Key 生成器，格式为 {@code sk-} + UUID 去横线。
 */
public final class RoutingSecretKeyGenerator {

    private static final String PREFIX = "sk-";

    /**
     * 单把 key 的长度上限：生成器固定产出 35 字符（{@code sk-} + 32 hex），本值留有余量。
     * 不校验字符集与定长——库内仍有手工构造的 key（如 {@code sk-de…-0N}）。
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
     * 入站校验：只认 {@code sk-} 前缀 + 不超过 {@link #MAX_LENGTH} 字符，
     * 不校验字符集与定长。
     */
    public static boolean isRoutingSecretKey(String key) {
        return key != null
                && key.length() <= MAX_LENGTH
                && key.startsWith(PREFIX)
                && key.length() > PREFIX.length();
    }
}
