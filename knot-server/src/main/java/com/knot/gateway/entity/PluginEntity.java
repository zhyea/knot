package com.knot.gateway.entity;

public class PluginEntity {
    private Long id;
    private String code;
    private String name;
    private String pluginType;
    private String version;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPluginType() { return pluginType; }
    public void setPluginType(String pluginType) { this.pluginType = pluginType; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
