package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.ScheduledTaskEntity;
import org.chobit.knot.dal.entity.ScheduledTaskRunEntity;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunDto;

import java.time.LocalDateTime;

/**
 * {@code ScheduledTask*} 实体 ⇄ {@code svc.dto.system} 的 record 转换。
 *
 * 全模块只有本类允许 import {@code org.chobit.knot.dal.entity}（另有 {@code svc.service} 的两个写库服务用 Mapper）。
 * 不做任何兜底：null 就是 null，任务名的空串与 null 是两种输入，归一在管理端。
 * 加实体字段时本类必须同步，否则对外 JSON 会少一个键。
 */
public final class ScheduledTaskConverter {

    private ScheduledTaskConverter() {
    }

    public static ScheduledTaskDto toTaskRecord(ScheduledTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ScheduledTaskDto(
                entity.getId(),
                entity.getTaskCode(),
                entity.getTaskName(),
                entity.getHandlerCode(),
                entity.getCronExpression(),
                entity.getExecutionMode(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getLastFireAt(),
                entity.getNextFireAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static ScheduledTaskEntity toTaskEntity(ScheduledTaskDto record) {
        if (record == null) {
            return null;
        }
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        entity.setId(record.id());
        entity.setTaskCode(record.taskCode());
        entity.setTaskName(record.taskName());
        entity.setHandlerCode(record.handlerCode());
        entity.setCronExpression(record.cronExpression());
        entity.setExecutionMode(record.executionMode());
        entity.setStatus(record.status());
        entity.setDescription(record.description());
        entity.setLastFireAt(record.lastFireAt());
        entity.setNextFireAt(record.nextFireAt());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }

    public static ScheduledTaskRunDto toRunRecord(ScheduledTaskRunEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ScheduledTaskRunDto(
                entity.getId(),
                entity.getTaskId(),
                entity.getTaskCode(),
                entity.getTaskName(),
                entity.getExecutionMode(),
                entity.getNodeId(),
                entity.getTriggerType(),
                entity.getStatus(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getDurationMs(),
                entity.getAffectedRows(),
                entity.getErrorMsg(),
                entity.getCreatedAt());
    }

    /**
     * 收尾专用的实体：只填 {@code updateRun} 那条 UPDATE 会写的字段。
     *
     * 接口入参只有结果字段，不接收整条记录，{@code taskCode} / {@code nodeId} 等执行开始时的信息不可改。
     */
    public static ScheduledTaskRunEntity newRunForFinish(Long id,
                                                         Integer status,
                                                         LocalDateTime endTime,
                                                         Long durationMs,
                                                         Integer affectedRows,
                                                         String errorMsg) {
        ScheduledTaskRunEntity entity = new ScheduledTaskRunEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setEndTime(endTime);
        entity.setDurationMs(durationMs);
        entity.setAffectedRows(affectedRows);
        entity.setErrorMsg(errorMsg);
        return entity;
    }

    public static ScheduledTaskRunEntity toRunEntity(ScheduledTaskRunDto record) {
        if (record == null) {
            return null;
        }
        ScheduledTaskRunEntity entity = new ScheduledTaskRunEntity();
        entity.setId(record.id());
        entity.setTaskId(record.taskId());
        entity.setTaskCode(record.taskCode());
        entity.setTaskName(record.taskName());
        entity.setExecutionMode(record.executionMode());
        entity.setNodeId(record.nodeId());
        entity.setTriggerType(record.triggerType());
        entity.setStatus(record.status());
        entity.setStartTime(record.startTime());
        entity.setEndTime(record.endTime());
        entity.setDurationMs(record.durationMs());
        entity.setAffectedRows(record.affectedRows());
        entity.setErrorMsg(record.errorMsg());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }
}
