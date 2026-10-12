package org.chobit.knot.gateway.constants;

/**
 * Executes the public operation. Executes the public operation.
 */
public final class GatewayHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String RULE = "Rule";
    public static final String TRACEPARENT = "traceparent";
    /**
     * 粘性路由键：同一会话/终端用户多轮请求带上同一个值，网关据此把请求稳定落到
     * 模型池内的同一个上游，从而命中上游 prompt cache。默认头名与
     * {@code StickyPolicy.HEADER_NAME_DEFAULT} 一致（池级配置可改，但控制器固定读此头）。
     */
    public static final String X_KNOT_STICKY_KEY = "X-Knot-Sticky-Key";
    public static final String X_API_KEY = "x-api-key";
    public static final String ANTHROPIC_VERSION = "anthropic-version";

    private GatewayHeaders() {
    }
}
