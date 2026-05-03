package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class OperationLogEntity {
    private Long id;
    private String moduleCode;
    private String actionCode;
    private String targetId;
    private String resultStatus;
}
