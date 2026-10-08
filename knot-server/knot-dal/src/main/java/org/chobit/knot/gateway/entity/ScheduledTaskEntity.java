package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduledTaskEntity {
    private Long id;
    private String taskCode;
    private String taskName;
    private String handlerCode;
    private String cronExpression;
    private String executionMode;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String description;
    private LocalDateTime lastFireAt;
    private LocalDateTime nextFireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
