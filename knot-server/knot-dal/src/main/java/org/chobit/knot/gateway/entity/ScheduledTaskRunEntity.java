package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduledTaskRunEntity {
    private Long id;
    private Long taskId;
    private String taskCode;
    private String taskName;
    private String executionMode;
    private String nodeId;
    private String triggerType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private Integer affectedRows;
    private String errorMsg;
    private LocalDateTime createdAt;
}
