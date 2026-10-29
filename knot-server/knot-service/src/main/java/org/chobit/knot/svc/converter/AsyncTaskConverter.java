package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDto;

/**
 * {@code AsyncTaskEntity} / {@code AsyncTaskProfileEntity} ⇄ {@code svc.dto.async} 的 record 模型转换。
 *
 * <p>⭐ <b>这是 dal 与服务层之间的唯一一道门</b>：全模块只有本类允许 import
 * {@code org.chobit.knot.dal.entity}（Mapper 也只有 {@code svc.service} 的写库服务用）。
 * 收敛到一个类，好处不是「少写几行」，而是「将来要检查有没有实体漏出去」时
 * 有一条命令就能出结果的判据（grep {@code org.chobit.knot.dal} 应只命中本类与两个写库服务）。</p>
 *
 * <p>⚠ <b>不做任何兜底与归一</b>：字段为 null 就是 null，空串就是空串。
 * 档案里的默认值、时间推导、表达式合法性都归消费方（{@code AsyncTaskSpec}）的模型，
 * 这里补一层兜底就会出现两个「默认值出处」，必然漂移。</p>
 *
 * <p>⚠ <b>加实体字段时这里必须同步</b>：漏一个字段编译不会报错，表现是「新字段读出来恒为 null」，
 * 而且只在用到那个字段的场景才显形。守门测试 {@code AsyncTaskConverterTest} 按字段数钉住。</p>
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
                entity.getNextPollAt(),
                entity.getClaimId(),
                entity.getRequestJson(),
                entity.getPollSpecJson(),
                entity.getContextJson(),
                entity.getResultJson(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getBillingRuleCode(),
                entity.getBillingRuleVersionCode(),
                entity.getSubmittedAt(),
                entity.getCompletedAt(),
                entity.getExpiresAt(),
                entity.getResultExpiresAt());
    }

    /**
     * 草稿 → 实体。{@code status} / {@code attempts} 在这里落成初始值：
     * 新任务必然是 {@link AsyncTaskStatusEnum#PENDING}、查询次数必然是 0，
     * 不由调用方传——否则调用方就能把一条新任务直接建成终态。
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
                entity.getResultExpiresDays(),
                entity.getPollIntervalMs(),
                entity.getPollBackoffBaseMs(),
                entity.getPollBackoffMaxMs(),
                entity.getMaxAttempts(),
                entity.getTimeoutMs());
    }
}
