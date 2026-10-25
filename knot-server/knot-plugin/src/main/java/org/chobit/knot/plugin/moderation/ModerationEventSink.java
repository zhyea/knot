package org.chobit.knot.plugin.moderation;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.common.util.JsonKit;
/**
 * 命中事件出口。<b>已确认：不必等 Kafka 就绪</b> —— 本期结构化日志先行，运维侧采集即用；
 * Kafka 配齐后在本类内切到 {@code ${prefix}.moderation.hits} topic，主链路无感。
 *
 * <p>与 {@code PluginLogWriter} 的分工：后者是<b>链路日志</b>（请求/响应/错误的原始载荷，
 * payload 受 {@code PluginLogPayload} sealed 约束）；本类是<b>业务事件</b>，面向数仓，
 * 只带元数据、不含原文，故不并入 sealed 契约。</p>
 */
@Slf4j
public class ModerationEventSink {

    /**
     * 输出命中事件；null 入参静默忽略。
     */
    public void emit(ModerationHitEvent event) {
        if (event == null) {
            return;
        }
        log.info("moderation-hit {}", JsonKit.toJson(event));
    }
}
