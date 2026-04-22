package com.knot.gateway.entity;

public class ProviderEntity {
    private Long id;
    private String code;
    private String name;
    private String providerType;
    private String baseUrl;
    private String status;
    private String rateLimitJson;
    private String quotaJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRateLimitJson() { return rateLimitJson; }
    public void setRateLimitJson(String rateLimitJson) { this.rateLimitJson = rateLimitJson; }
    public String getQuotaJson() { return quotaJson; }
    public void setQuotaJson(String quotaJson) { this.quotaJson = quotaJson; }
}
