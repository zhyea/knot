package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;
import org.chobit.knot.common.constants.enums.AsyncTaskDeliveryStatusEnum;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileSaveDto;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDetailVo;

/**
 * {@code AsyncTaskEntity} / {@code AsyncTaskProfileEntity} ⇄ {@code svc.dto.async} 的 record 模型转换。
 *
 * 全模块只有本类允许 import {@code org.chobit.knot.dal.entity}（Mapper 仅 {@code svc.service} 的写库服务使用）。
 * 不做任何兜底与归一：null 即 null、空串即空串，默认值与时间推导归消费方（{@code AsyncTaskSpec}）。
 * 加实体字段时本类必须同步，否则新字段读出来恒为 {@code null}。
 */
public final class AsyncTaskConverter {

    private AsyncTaskConverter() {
    }

    public static AsyncTaskDto toRecord(AsyncTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AsyncTaskDto(
                entity.getId(),
                entity.getTaskCode(),
                entity.getProfileCode(),
                entity.getProtocol(),
                entity.getUpstreamTaskId(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getRetryRound(),
                entity.getNextPollAt(),
                entity.getClaimId(),
                entity.getRequestJson(),
                entity.getPollSpecJson(),
                entity.getContextJson(),
                entity.getResultJson(),
                entity.getTerminalBody(),
                entity.getSettlementJson(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getBillingRuleCode(),
                entity.getBillingRuleVersionCode(),
                entity.getSubmittedAt(),
                entity.getCompletedAt(),
                entity.getExpiresAt(),
                entity.getResultExpiresAt(),
                entity.getCallbackUrl(),
                entity.getDeliveryStatus(),
                entity.getDeliveryAttempts(),
                entity.getNextDeliveryAt(),
                entity.getDeliveredAt(),
                entity.getDeliveryError());
    }

    /**
     * 草稿 → 实体。{@code status} 落 {@link AsyncTaskStatusEnum#PENDING}、{@code attempts} 落 0，不由调用方传入。
     */
    public static AsyncTaskEntity toEntity(AsyncTaskDraftDto draft) {
        AsyncTaskEntity entity = new AsyncTaskEntity();
        entity.setTaskCode(draft.taskCode());
        entity.setProfileCode(draft.profileCode());
        entity.setProtocol(draft.protocol());
        entity.setUpstreamTaskId(draft.upstreamTaskId());
        entity.setStatus(AsyncTaskStatusEnum.PENDING.code());
        entity.setAttempts(0);
        entity.setNextPollAt(draft.nextPollAt());
        entity.setRequestJson(draft.requestJson());
        entity.setPollSpecJson(draft.pollSpecJson());
        entity.setContextJson(draft.contextJson());
        entity.setBillingRuleCode(draft.billingRuleCode());
        entity.setBillingRuleVersionCode(draft.billingRuleVersionCode());
        entity.setSubmittedAt(draft.submittedAt());
        entity.setExpiresAt(draft.expiresAt());
        entity.setResultExpiresAt(draft.resultExpiresAt());
        // 交付维度的初值同样只在这里定：有回推地址 = 待回调，否则 = 无需回调。
        // 与 status/attempts 一样，调用方没有机会把一条新任务直接建成「已交付」。
        entity.setCallbackUrl(draft.callbackUrl());
        entity.setDeliveryStatus(draft.callbackUrl() == null
                ? AsyncTaskDeliveryStatusEnum.NONE.code()
                : AsyncTaskDeliveryStatusEnum.PENDING.code());
        entity.setDeliveryAttempts(0);
        return entity;
    }

    public static AsyncTaskProfileDto toProfileRecord(AsyncTaskProfileEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AsyncTaskProfileDto(
                entity.getProfileCode(),
                entity.getProtocol(),
                entity.getQueryMethod(),
                entity.getQueryPath(),
                entity.getTaskIdPath(),
                entity.getStatusPath(),
                entity.getStatusSucceeded(),
                entity.getStatusFailed(),
                entity.getStatusRunning(),
                entity.getResultPath(),
                entity.getResultUrlField(),
                entity.getErrorMessagePath(),
                entity.getResultExpiresDays(),
                entity.getPollIntervalMs(),
                entity.getPollBackoffBaseMs(),
                entity.getPollBackoffMaxMs(),
                entity.getMaxAttempts(),
                entity.getTimeoutMinutes(),
                entity.getUsagePath());
    }

    /**
     * 写入载体 → 实体（新建与更新共用）。
     *
     * 不含 {@code created_at} / {@code updated_at} / {@code binding_count}：前两个由 DB 维护，第三个是查询期派生列。
     */
    public static AsyncTaskProfileEntity toProfileEntity(AsyncTaskProfileSaveDto dto) {
        if (dto == null) {
            return null;
        }
        AsyncTaskProfileEntity entity = new AsyncTaskProfileEntity();
        entity.setProfileCode(dto.profileCode());
        entity.setProtocol(dto.protocol());
        entity.setQueryMethod(dto.queryMethod());
        entity.setQueryPath(dto.queryPath());
        entity.setTaskIdPath(dto.taskIdPath());
        entity.setStatusPath(dto.statusPath());
        entity.setStatusSucceeded(dto.statusSucceeded());
        entity.setStatusFailed(dto.statusFailed());
        entity.setStatusRunning(dto.statusRunning());
        entity.setResultPath(dto.resultPath());
        entity.setResultUrlField(dto.resultUrlField());
        entity.setErrorMessagePath(dto.errorMessagePath());
        entity.setResultExpiresDays(dto.resultExpiresDays());
        entity.setPollIntervalMs(dto.pollIntervalMs());
        entity.setPollBackoffBaseMs(dto.pollBackoffBaseMs());
        entity.setPollBackoffMaxMs(dto.pollBackoffMaxMs());
        entity.setMaxAttempts(dto.maxAttempts());
        entity.setTimeoutMinutes(dto.timeoutMinutes());
        entity.setStatus(dto.status());
        entity.setRemark(dto.remark());
        entity.setUsagePath(dto.usagePath());
        return entity;
    }

    /** 实体 → 管理面视图（含被引用行数与时间戳） */
    public static AsyncTaskProfileDetailVo toProfileDetailVo(AsyncTaskProfileEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AsyncTaskProfileDetailVo(
                entity.getId(),
                entity.getProfileCode(),
                entity.getProtocol(),
                entity.getQueryMethod(),
                entity.getQueryPath(),
                entity.getTaskIdPath(),
                entity.getStatusPath(),
                entity.getStatusSucceeded(),
                entity.getStatusFailed(),
                entity.getStatusRunning(),
                entity.getResultPath(),
                entity.getResultUrlField(),
                entity.getErrorMessagePath(),
                entity.getResultExpiresDays(),
                entity.getPollIntervalMs(),
                entity.getPollBackoffBaseMs(),
                entity.getPollBackoffMaxMs(),
                entity.getMaxAttempts(),
                entity.getTimeoutMinutes(),
                entity.getStatus(),
                entity.getRemark(),
                entity.getBindingCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getUsagePath());
    }
}
