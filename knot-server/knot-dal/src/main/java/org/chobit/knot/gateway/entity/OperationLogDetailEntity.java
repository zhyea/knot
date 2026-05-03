package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class OperationLogDetailEntity {
    private Long logId;
    private String beforeJson;
    private String afterJson;
}
