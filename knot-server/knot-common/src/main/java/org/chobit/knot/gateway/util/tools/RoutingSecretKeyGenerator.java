package org.chobit.knot.gateway.util.tools;

import java.util.UUID;

/**
 * Executes the public operation. Executes the public operation.
 */
/**
 * 璺敱瑙勫垯 API Key 鐢熸垚鍣紝鏍煎紡锛歴k-{uuid 鍘绘í绾縸
 */
public final class RoutingSecretKeyGenerator {

    private static final String PREFIX = "sk-";

    private RoutingSecretKeyGenerator() {
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static String generate() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public static boolean isRoutingSecretKey(String key) {
        return key != null && key.startsWith(PREFIX) && key.length() > PREFIX.length();
    }
}
