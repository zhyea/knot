package com.knot.gateway.entity;

public class BackupJobEntity {
    private Long id;
    private String jobCode;
    private String status;
    private String snapshotRef;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSnapshotRef() { return snapshotRef; }
    public void setSnapshotRef(String snapshotRef) { this.snapshotRef = snapshotRef; }
}
