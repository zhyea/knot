package org.chobit.knot.svc.dto.async;

import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;

import java.time.LocalDateTime;

/**
 * 新建一条异步任务的输入（对应一次 {@code INSERT}）。
 *
 * <p>⭐ <b>为什么与 {@link AsyncTaskDto} 分开</b>：建任务时 {@code id} / {@code claimId} /
 * {@code resultJson} 这些字段还不存在，塞进同一个模型就得允许它们为 null，
 * 于是每个读字段的地方都要判空——把「这一刻哪些字段必然有值」这个事实抹掉了。
 * 分开之后：草稿只含创建方能决定的字段，{@code status} / {@code attempts} 由
 * {@code AsyncTaskService} 落成 {@link AsyncTaskStatusEnum#PENDING} 与 0，
 * 调用方没有机会把一条新任务直接建成终态。</p>
 *
 * <p>{@code nextPollAt} / {@code expiresAt} / {@code resultExpiresAt} 由 gateway 侧按档案算好传入
 * （口径在 {@code AsyncTaskSupport#build}），svc 不做时间推导：档案语义属于调用方，
 * 存储层不该知道「轮询间隔」这种概念。</p>
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
                              *
                              * <p>⚠ 它从<b>客户端原始请求体</b>里提取（提交侧），
                              * 但绝不会跟着请求体转发给上游——上游一旦直接推终态给客户端，
                              * 这笔用量就绕过了轮询结算。</p>
                              */
                             String callbackUrl) {
}
