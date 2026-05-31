package org.chobit.knot.gateway.util.tools;

import java.util.UUID;

/**
 * Executes the public operation. Executes the public operation.
 */
/**
 * 璺敱瑙勫垯缂栫爜鐢熸垚鍣紝鏍煎紡锛?2 浣嶅崄鍏繘鍒讹紙UUID 鍘绘í绾匡級
 */
public final class RoutingRuleCodeGenerator {

    private RoutingRuleCodeGenerator() {
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
