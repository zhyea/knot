package org.chobit.knot.svc.vo.system;

import java.time.LocalDateTime;

/**
 * 操作日志完整行（管理面日志页 / 各实体「操作记录」抽屉）。
 * 与 {@link OperationLogItem} 不是一回事：后者是 {@code /api/system/logs} 看板用的瘦投影
 * （只有 moduleCode / actionCode / targetId / resultStatus 5 个字段）；本载体摊开新旧值、错误信息、执行耗时。
 * 两者共用同一张表，不能合并。
 * {@code oldValue} / {@code newValue} 是已脱敏、已裁掉未变化字段的 JSON 串，
 * 脱敏与裁剪在 {@code OperationLogSnapshotSupport}，不在本载体。
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
