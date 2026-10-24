package org.chobit.knot.gateway.constants.enums;

/**
 * 网关失败阶段：回答「错误发生在链路的哪一段」，与 {@link GatewayErrorTypeEnum}
 * （回答「这是什么性质的错误」）正交。
 *
 * <p>只有数仓出口消费本枚举（HTTP 响应体不含 stage）：定位问题时「上游调用超时」与
 * 「请求解析超时」的处置完全不同，光靠 type 区分不出来。</p>
 */
public enum GatewayErrorStageEnum {

    /** 请求头 / 鉴权校验 */
    HEADER_VALIDATE("header_validate"),

    /** 请求体解析 */
    REQUEST_PARSE("request_parse"),

    /** 规则级流量检查（应用 / 规则 / 消费者维度） */
    ROUTING_TRAFFIC_CHECK("routing_traffic_check"),

    /** 目标级流量检查（模型 / 供应商维度） */
    TARGET_TRAFFIC_CHECK("target_traffic_check"),

    /** 上游调用 */
    UPSTREAM_CALL("upstream_call"),

    /** 插件拦截（如敏感词） */
    PLUGIN_MODERATION("plugin_moderation"),

    /** 共享状态存储（Redis）不可用 */
    STORAGE("storage"),

    /** 全局请求预算超时（B2 deadline） */
    TIMEOUT("timeout"),

    /** 未预期内部故障 */
    INTERNAL("internal");

    private final String code;

    GatewayErrorStageEnum(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
