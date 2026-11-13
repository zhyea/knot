package org.chobit.knot.svc.dto.async;

import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;

import java.time.LocalDateTime;

/**
 * 新建一条异步任务的输入（对应一次 {@code INSERT}）。
 *
 * 只含创建方能决定的字段：{@code status} / {@code attempts} 由 {@code AsyncTaskService} 落成
 * {@link AsyncTaskStatusEnum#PENDING} 与 0。{@code nextPollAt} / {@code expiresAt} / {@code resultExpiresAt}
 * 由 gateway 侧按档案算好传入（{@code AsyncTaskSupport#build}），svc 不做时间推导。
 */
public record AsyncTaskDraftDto(String taskCode,
                             String profileCode,
                             String protocol,
                             String upstreamTaskId,
                             LocalDateTime nextPollAt,
                             String requestJson,
                             String pollSpecJson,
                             String contextJson,
                             String billingRuleCode,
                             String billingRuleVersionCode,
                             LocalDateTime submittedAt,
                             LocalDateTime expiresAt,
                             LocalDateTime resultExpiresAt,
                             /**
                              * 客户端回推地址；{@code null} 表示本次任务不回调。
                              * 取自客户端原始请求体，不随请求体转发给上游。
                              */
                             String callbackUrl) {
}
