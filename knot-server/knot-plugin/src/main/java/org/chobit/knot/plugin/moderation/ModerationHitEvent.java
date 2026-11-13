package org.chobit.knot.plugin.moderation;

/**
 * 敏感词命中事件：只带元数据，不带命中词、不带上下文片段、不带原文。
 *
 * 当前经结构化日志输出，事件结构不随 sink 改变；消费者维度暂不落事件（路由快照只带 {@code consumerId}）。
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
