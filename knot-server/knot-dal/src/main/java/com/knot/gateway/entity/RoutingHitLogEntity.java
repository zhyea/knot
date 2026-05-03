package com.knot.gateway.entity;

import java.time.LocalDateTime;

public class RoutingHitLogEntity {
    private Long id;
    private Long ruleId;
    private String fromTarget;
    private String toTarget;
    private String reason;
    private LocalDateTime hitTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getFromTarget() { return fromTarget; }
    public void setFromTarget(String fromTarget) { this.fromTarget = fromTarget; }
    public String getToTarget() { return toTarget; }
    public void setToTarget(String toTarget) { this.toTarget = toTarget; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getHitTime() { return hitTime; }
    public void setHitTime(LocalDateTime hitTime) { this.hitTime = hitTime; }
}
