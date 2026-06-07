package org.chobit.knot.gateway.util.tools;

import java.util.UUID;

/**
 * 路由规则编码生成器，默认使用去横线的 UUID。
 */
public final class RoutingRuleCodeGenerator {

    private RoutingRuleCodeGenerator() {
    }

    /**
     * Generates a new routing rule code.
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
