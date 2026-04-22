package com.knot.gateway.entity;

import java.time.LocalDateTime;

public class GrayPlanEntity {
    private Long id;
    private String planName;
    private String targetType;
    private Long targetId;
    private Integer trafficPercent;
    private String stepsJson;
    private LocalDateTime startTime;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Integer getTrafficPercent() { return trafficPercent; }
    public void setTrafficPercent(Integer trafficPercent) { this.trafficPercent = trafficPercent; }
    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
