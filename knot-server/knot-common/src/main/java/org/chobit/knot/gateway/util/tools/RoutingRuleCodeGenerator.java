package org.chobit.knot.gateway.util.tools;

import java.util.UUID;

/**
 * 路由规则编码生成器，格式：32 位十六进制（UUID 去横线）
 */
public final class RoutingRuleCodeGenerator {

    private RoutingRuleCodeGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
