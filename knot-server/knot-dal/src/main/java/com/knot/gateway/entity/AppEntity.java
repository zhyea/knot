package com.knot.gateway.entity;

public class AppEntity {
    private Long id;
    private String appId;
    private String name;
    private Long ownerUserId;
    private String status;
    private String rateLimitJson;
    private String quotaJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRateLimitJson() { return rateLimitJson; }
    public void setRateLimitJson(String rateLimitJson) { this.rateLimitJson = rateLimitJson; }
    public String getQuotaJson() { return quotaJson; }
    public void setQuotaJson(String quotaJson) { this.quotaJson = quotaJson; }
}
