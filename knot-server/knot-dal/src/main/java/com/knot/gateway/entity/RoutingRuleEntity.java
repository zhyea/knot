package com.knot.gateway.entity;

public class RoutingRuleEntity {
    private Long id;
    private String name;
    private String strategyType;
    private Integer priority;
    private String conditionExpr;
    private Long targetProviderId;
    private Long targetModelId;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStrategyType() { return strategyType; }
    public void setStrategyType(String strategyType) { this.strategyType = strategyType; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getConditionExpr() { return conditionExpr; }
    public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }
    public Long getTargetProviderId() { return targetProviderId; }
    public void setTargetProviderId(Long targetProviderId) { this.targetProviderId = targetProviderId; }
    public Long getTargetModelId() { return targetModelId; }
    public void setTargetModelId(Long targetModelId) { this.targetModelId = targetModelId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
