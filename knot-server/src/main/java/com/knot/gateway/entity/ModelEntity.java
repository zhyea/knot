package com.knot.gateway.entity;

public class ModelEntity {
    private Long id;
    private Long providerId;
    private String modelCode;
    private String name;
    private String modelType;
    private String version;
    private String status;
    private String rateLimitJson;
    private String quotaJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRateLimitJson() { return rateLimitJson; }
    public void setRateLimitJson(String rateLimitJson) { this.rateLimitJson = rateLimitJson; }
    public String getQuotaJson() { return quotaJson; }
    public void setQuotaJson(String quotaJson) { this.quotaJson = quotaJson; }
}
