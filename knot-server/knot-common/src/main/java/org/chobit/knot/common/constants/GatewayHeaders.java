package org.chobit.knot.common.constants;

public final class GatewayHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String RULE = "Rule";
    public static final String TRACEPARENT = "traceparent";
    /**
     * 网关自生成的请求标识：请求进入网关时生成 32 位小写 hex，随请求头透传给上游，供应商侧日志可直接检索。
     * 与 {@code upstream_request_id} 严格区分——本头是我方发出去的 id，后者是上游回来的 id，不可互相替代。
     * 客户端传入的同名请求头一律忽略（网关是唯一权威）。
     */
    public static final String X_REQUEST_ID = "X-Request-Id";
    /**
     * 粘性路由键：同一会话/终端用户多轮请求带上同一个值，网关据此把请求稳定落到
     * 模型池内的同一个上游，从而命中上游 prompt cache。默认头名与
     * {@code StickyPolicy.HEADER_NAME_DEFAULT} 一致（池级配置可改，但控制器固定读此头）。
     */
    public static final String X_KNOT_STICKY_KEY = "X-Knot-Sticky-Key";
    /**
     * 管理面路由规则测试专用：请求头携带 {@code true} 时，本次调用临时开启返回消费详情
     * （{@code knot_extend} 注入），无视该消费者 {@code return_usage_detail} 的关闭配置。
     * 响应只多出本次调用自己的用量与网关账目，不带头即维持原配置。
     */
    public static final String X_KNOT_TEST_RETURN_USAGE = "X-Knot-Test-Return-Usage";
    /**
     * 首 token 响应时间（TTFT，毫秒）：请求进入网关模板处理 → 响应侧首个可观测产出
     * （流式=上游首行数据，非流式=响应体就绪）的耗时。端到端口径，含网关自身鉴权、
     * 路由解析、插件、频控与重试/failover 耗时；异常路径不设置，头缺失即本次无成功首 token。
     */
    public static final String X_KNOT_FIRST_TOKEN_TIME = "X-Knot-First-Token-Time";
    /**
     * 实际承接本次请求的模型业务码（kb_models.model_code）：池内选中、粘性命中、failover 顺延之后
     * 真正调用上游的那个模型。异常路径不设置，头缺失即本次没有成功调用任何上游模型。
     */
    public static final String X_KNOT_MODEL = "X-Knot-Model";
    /**
     * 网关管理面共享令牌：访问 {@code /_admin/*} 端点时携带，与配置的令牌比对。
     * 未配置令牌时仅放行 loopback 来源（见 {@code OutageAdminController}）。
     */
    public static final String X_KNOT_ADMIN_TOKEN = "X-Knot-Admin-Token";
    public static final String X_API_KEY = "x-api-key";
    public static final String ANTHROPIC_VERSION = "anthropic-version";

    /**
     * {@link #X_KNOT_TEST_RETURN_USAGE} 的取值解析：仅 {@code true}（忽略大小写与首尾空白）生效。
     */
    public static boolean testReturnUsageRequested(String headerValue) {
        return headerValue != null && Boolean.parseBoolean(headerValue.trim());
    }

    private GatewayHeaders() {
    }
}
