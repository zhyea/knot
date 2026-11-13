package org.chobit.knot.plugin.moderation;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.common.util.JsonKit;
/**
 * 敏感词命中事件出口：当前输出结构化日志，事件只带元数据、不含原文。
 *
 * 与 {@code PluginLogWriter} 的分工：后者是链路日志（各阶段载荷，payload 受 {@code PluginLogPayload} sealed 约束），
 * 本类是面向数仓的业务事件，不并入 sealed 契约。
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
