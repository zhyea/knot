package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.ScheduledTaskEntity;
import org.chobit.knot.dal.entity.ScheduledTaskRunEntity;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunDto;

import java.time.LocalDateTime;

/**
 * {@code ScheduledTask*} 实体 ⇄ {@code svc.dto.system} 的 record 转换。
 *
 * <p>⭐ 与 {@code AsyncTaskConverter} 一样：<b>全模块只有本类允许 import
 * {@code org.chobit.knot.dal.entity}</b>（另加 {@code svc.service} 的两个写库服务用 Mapper）。
 * 「有没有实体漏出去」因此是一条 grep 就能出结果的事。</p>
 *
 * <p>⚠ <b>不做任何兜底</b>：字段为 null 就是 null。任务名空串与 null 是两种输入，
 * 归一在管理端（那里才知道「空串该不该存」）。</p>
 *
 * <p>⚠ <b>加实体字段时这里必须同步</b>：这里漏一个字段的表现不是报错，
 * 而是「对外 JSON 少一个键」——后端测试全绿、前端悄悄破。守门测试
 * {@code ScheduledTaskConverterTest} 按字段数钉住。</p>
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
     * <p>⭐ <b>为什么要有它，而不是让 store 自己 new 一个实体再 setter</b>：
     * 那样 store 就得出现实体类型名，「只有 Converter 碰实体」这条红线就破了。
     * 把构造收进这里，store 侧只需要 {@code mapper.updateRun(...)} 一个调用。</p>
     *
     * <p>⚠ <b>刻意不接收整个记录再转换</b>：接口入参只有结果字段，
     * 若允许传整个记录，就等于允许顺手把 {@code taskCode} / {@code nodeId} 这些
     * <b>执行开始时的事实</b>也改掉——一条记录会自相矛盾（跑在 A 节点、记录说是 B 节点）。</p>
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
