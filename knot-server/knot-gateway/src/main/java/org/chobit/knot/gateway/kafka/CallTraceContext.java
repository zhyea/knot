package org.chobit.knot.gateway.kafka;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.kafka.msg.AttemptMsgDto;
import org.chobit.knot.gateway.kafka.msg.AttemptOutcomeMsg;
import org.chobit.knot.gateway.kafka.msg.UpstreamErrorMsgDto;
import org.chobit.knot.gateway.outage.EjectionDecision;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次请求的上游调用轨迹：候选循环里逐次写入，请求终态时快照成响应层的 {@code attempts[]}。
 *
 * <p>存在 {@code GatewayRequestContext} 的属性里（键 {ATTRIBUTE}）：候选循环在
 * {@code GatewayRequestHandler} 内部，而发布点在父类模板，两者只能通过上下文传递。</p>
 *
 * <p>为什么要把每次上游调用都留下来：一次客户端请求可能顺延多个候选、每个候选可能重投多次，
 * 只有 kept 到 attempt 粒度才算得出「单个模型的失败率」。请求层/响应层两级粒度做统计，
 * attempt 明细又不独立成表，靠的就是这个内嵌轨迹。</p>
 */
public final class CallTraceContext {

    /**
     * 上下文属性键。
     */
    public static final String ATTRIBUTE = "callTrace";

    private final List<AttemptDraft> drafts = new ArrayList<>();
    private boolean failOpen;

    /**
     * 登记一个候选：无论最终是被跳过、被频控拒绝还是发起调用，都要留痕。
     */
    public AttemptDraft newAttempt(RoutingRuleTargetDto target, EjectionDecision decision) {
        AttemptDraft draft = new AttemptDraft(target);
        if (decision != null && decision.skip()) {
            draft.skipped();
        }
        if (decision != null && decision.probing()) {
            draft.probeRequest = true;
        }
        drafts.add(draft);
        return draft;
    }

    /**
     * 候选被摘除一空后是否走了 fail-open 重跑（这将支配 generates 第二轮候选调用）。
     */
    public void failOpen() {
        this.failOpen = true;
    }

    /**
     * 快照成消息里的 attempts 列表；seq 按登记顺序从 1 开始。
     */
    public List<AttemptMsgDto> records() {
        List<AttemptMsgDto> records = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            records.add(drafts.get(index).toRecord(index + 1));
        }
        return records;
    }

    /**
     * 因异常摘除被跳过的候选数：<b>不是限流拒绝</b>，统计时勿混为一谈。
     */
    public int ejectedSkipped() {
        int count = 0;
        for (AttemptDraft draft : drafts) {
            if (AttemptOutcomeMsg.SKIPPED == draft.outcome) {
                count++;
            }
        }
        return count;
    }

    /**
     * 重试总次数：每个候选内部的额外试投次数之和（首次调用不计）。
     */
    public int retryTotal() {
        int total = 0;
        for (AttemptDraft draft : drafts) {
            total += Math.max(0, draft.attempts - 1);
        }
        return total;
    }

    /**
     * 顺延次数：成功调用之前失败过的候选数。
     */
    public int failoverTotal() {
        int failures = 0;
        for (AttemptDraft draft : drafts) {
            if (AttemptOutcomeMsg.SUCCESS == draft.outcome) {
                return failures;
            }
            failures++;
        }
        return failures;
    }

    /**
     * 最后一次登记的重试偏爱。
     */
    public boolean failOpenApplied() {
        return failOpen;
    }

    /**
     * 单个候选的调用轨迹（可变草稿），快照时转 {@link AttemptMsgDto}。
     */
    public static final class AttemptDraft {

        private final RoutingRuleTargetDto target;
        private AttemptOutcomeMsg outcome = AttemptOutcomeMsg.SKIPPED;
        private boolean skippedByEjection;
        private boolean probeRequest;
        private boolean trafficRejected;
        private String rejectReason;
        private int attempts;
        private int maxAttempts;
        private Integer upstreamHttpStatus;
        private String upstreamErrorCode;
        private Long retryAfterMs;
        private String upstreamRequestId;
        private boolean countedForEjection;

        private AttemptDraft(RoutingRuleTargetDto target) {
            this.target = target;
        }

        /**
         * 因异常摘除被跳过：既不消耗该目标的频控预算，也不是被限流。
         */
        public void skipped() {
            this.outcome = AttemptOutcomeMsg.SKIPPED;
            this.skippedByEjection = true;
        }

        /**
         * 目标自身被频控 / 额度拒绝。
         */
        public void rejected(String reason) {
            this.outcome = AttemptOutcomeMsg.TRAFFIC_REJECTED;
            this.trafficRejected = true;
            this.rejectReason = reason;
        }

        /**
         * 发起一次试投（含首次）。
         */
        public void nextAttempt(int maxAttempts) {
            this.attempts++;
            this.maxAttempts = Math.max(1, maxAttempts);
            this.outcome = AttemptOutcomeMsg.UPSTREAM_FAILURE;
        }

        /**
         * 本次候选调用成功。
         *
         * @param upstreamRequestId 供应商侧请求 id（响应头白名单归一）；无命中传 null
         */
        public void succeeded(String upstreamRequestId) {
            this.outcome = AttemptOutcomeMsg.SUCCESS;
            this.upstreamRequestId = upstreamRequestId;
        }

        /**
         * 本次候选调用以上游异常告终。
         *
         * @param counted 是否计入摘除判定（5xx / 429 计，4xx 业务错与配置类错误不计）
         */
        public void failed(GatewayUpstreamException error, boolean counted) {
            this.outcome = AttemptOutcomeMsg.UPSTREAM_FAILURE;
            this.countedForEjection = counted;
            if (error != null) {
                this.upstreamHttpStatus = error.httpStatus();
                this.upstreamErrorCode = error.code();
                this.retryAfterMs = error.retryAfterMs();
                this.upstreamRequestId = error.upstreamRequestId();
            }
        }

        private AttemptMsgDto toRecord(int seq) {
            UpstreamErrorMsgDto upstream = null;
            if (upstreamHttpStatus != null || upstreamErrorCode != null || retryAfterMs != null) {
                upstream = new UpstreamErrorMsgDto(upstreamHttpStatus, upstreamErrorCode, retryAfterMs);
            }
            return new AttemptMsgDto(
                seq,
                target == null ? null : target.targetCode(),
                target == null ? null : target.targetType(),
                target == null ? null : target.upstreamModelCode(),
                target == null ? 0 : target.priority(),
                target != null && target.primary(),
                outcome.code(),
                skippedByEjection,
                probeRequest,
                attempts,
                maxAttempts,
                trafficRejected,
                rejectReason,
                upstream,
                upstreamRequestId,
                countedForEjection);
        }
    }
}
