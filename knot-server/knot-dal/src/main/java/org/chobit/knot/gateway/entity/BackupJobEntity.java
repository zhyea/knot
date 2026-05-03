package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class BackupJobEntity {
    private Long id;
    private String jobCode;
    private String status;
    private String snapshotRef;
}
