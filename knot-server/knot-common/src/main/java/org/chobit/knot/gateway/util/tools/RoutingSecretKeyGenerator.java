package org.chobit.knot.gateway.util.tools;

import java.util.UUID;

/**
 * 路由规则 API Key 生成器，格式：sk-{uuid 去横线}
 */
public final class RoutingSecretKeyGenerator {

    private static final String PREFIX = "sk-";

    private RoutingSecretKeyGenerator() {
    }

    public static String generate() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    public static boolean isRoutingSecretKey(String key) {
        return key != null && key.startsWith(PREFIX) && key.length() > PREFIX.length();
    }
}
