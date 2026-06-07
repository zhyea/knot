package org.chobit.knot.gateway.util.tools;

import java.util.UUID;

/**
 * 消费者 API Key 生成器，格式为 {@code sk-} + UUID 去横线。
 */
public final class RoutingSecretKeyGenerator {

    private static final String PREFIX = "sk-";

    private RoutingSecretKeyGenerator() {
    }

    /**
     * Generates a new consumer API key.
     */
    public static String generate() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns whether the supplied key matches the expected format.
     */
    public static boolean isRoutingSecretKey(String key) {
        return key != null && key.startsWith(PREFIX) && key.length() > PREFIX.length();
    }
}
