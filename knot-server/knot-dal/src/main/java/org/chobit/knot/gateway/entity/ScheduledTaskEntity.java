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
    private String status;
    private String description;
    private LocalDateTime lastFireAt;
    private LocalDateTime nextFireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
