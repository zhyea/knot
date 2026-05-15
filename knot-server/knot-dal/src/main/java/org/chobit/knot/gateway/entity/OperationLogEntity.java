package org.chobit.knot.gateway.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OperationLogEntity {
    private Long id;
    private String module;
    private String operation;
    private String entityType;
    private Long entityId;
    private String entityName;
    private String description;
    private String oldValue;
    private String newValue;
    private Long operatorId;
    private String operatorName;
    private String ipAddress;
    private String userAgent;
    private String status;
    private String errorMsg;
    private Long executionTime;
    private LocalDateTime createdAt;
}
