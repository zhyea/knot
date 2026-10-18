package org.chobit.knot.gateway.kafka;

/**
 * 一次上游调用的结局。
 */
public enum AttemptOutcomeMsg {

    /** 调用成功 */
    SUCCESS("success"),
    /** 以上游异常告终（重试耗尽或不可重试） */
    UPSTREAM_FAILURE("upstream_failure"),
    /** 被异常摘除跳过，压根没发请求 */
    SKIPPED("skipped"),
    /** 该目标自身被频控 / 额度拒绝 */
    TRAFFIC_REJECTED("traffic_rejected");

    private final String code;

    AttemptOutcomeMsg(String code) {
        this.code = code;
    }

    /**
     * 对外序列化的结局标识。
     */
    public String code() {
        return code;
    }
}
