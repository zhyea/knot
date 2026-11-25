package org.chobit.knot.svc.vo.system;

import java.time.LocalDateTime;

/**
 * 定时任务分页列表视图：只承载任务配置列表的列与定位标识。
 * 任务说明等编辑字段只在详情端点下发（铁律 12）。
 */
public record ScheduledTaskListItem(Long id,
                                    String taskCode,
                                    String taskName,
                                    String handlerCode,
                                    String cronExpression,
                                    String executionMode,
                                    Integer status,
                                    LocalDateTime lastFireAt,
                                    LocalDateTime nextFireAt) {
}
