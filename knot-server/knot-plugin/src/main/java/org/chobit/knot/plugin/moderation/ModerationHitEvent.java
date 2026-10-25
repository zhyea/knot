package org.chobit.knot.plugin.moderation;

/**
 * 敏感词命中事件：只带元数据，<b>不带命中词、不带上下文片段、不带原文</b>（合规：原文不出库）。
 *
 * <p>当前走结构化日志；Kafka 就绪后由 sink 切到 topic 送数仓，事件结构不变（设计文档 §5.3）。</p>
 *
 * <p>消费者维度暂不落事件：插件上下文的路由快照只带 {@code consumerId}，
 * 按项目「跨模块绑定一律存 code 不存 id」的口径，等快照补 consumerCode 后再加。</p>
 */
public record ModerationHitEvent(String traceId,
                                 String appCode,
                                 String ruleCode,
                                 /** REQUEST / RESPONSE */
                                 String direction,
                                 String categoryCode,
                                 int severity,
                                 /** 命中时生效的处置动作 */
                                 String action,
                                 int hitCount,
                                 /** 命中片段总长度（字符数），不含原文 */
                                 int matchedLength,
                                 /** 词库版本（已加载行的 MAX(updated_at) 毫秒值），供回溯 */
                                 long wordVersion,
                                 long timestampMillis) {

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static ModerationHitEvent of(String traceId,
                                        String appCode,
                                        String ruleCode,
                                        String direction,
                                        String categoryCode,
                                        int severity,
                                        String action,
                                        int hitCount,
                                        int matchedLength,
                                        long wordVersion) {
        return new ModerationHitEvent(traceId, appCode, ruleCode, direction,
                categoryCode, severity, action, hitCount, matchedLength, wordVersion,
                System.currentTimeMillis());
    }
}
