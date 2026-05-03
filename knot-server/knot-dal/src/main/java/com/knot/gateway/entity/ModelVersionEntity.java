package com.knot.gateway.entity;

public class ModelVersionEntity {
    private Long id;
    private Long modelId;
    private String version;
    private Integer grayPercent;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Integer getGrayPercent() { return grayPercent; }
    public void setGrayPercent(Integer grayPercent) { this.grayPercent = grayPercent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
