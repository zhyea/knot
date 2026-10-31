package org.chobit.knot.svc.vo.system;

import java.time.LocalDateTime;

/**
 * 操作日志<b>完整行</b>（管理面日志页 / 各实体「操作记录」抽屉）。
 *
 * <p>⚠ <b>与 {@link OperationLogItem} 不是一回事</b>：那个是 {@code /api/system/logs} 看板用的
 * 瘦投影（只有 moduleCode/actionCode/targetId/resultStatus），只有 5 个字段；
 * 这个是日志页要摊开新旧值、错误信息、执行耗时的完整载体。两者共用同一个表，
 * 但一个是「一眼看过去」、一个是「点开看细节」，不能合并。</p>
 *
 * <p>{@code oldValue}/{@code newValue} 是已脱敏 + 已裁掉未变化字段的 JSON 串
 * （脱敏与裁剪在 {@code OperationLogSnapshotSupport}，不在本载体）。</p>
 */
public record OperationLogDetailItem(
        Long id,
        String module,
        String operation,
        String entityType,
        Long entityId,
        String entityName,
        String description,
        String oldValue,
        String newValue,
        Long operatorId,
        String operatorName,
        String ipAddress,
        String userAgent,
        /** 执行结果（OperationLogStatusEnum）：1-成功 2-失败 */
        Integer status,
        String errorMsg,
        Long executionTime,
        LocalDateTime createdAt
) {
}
