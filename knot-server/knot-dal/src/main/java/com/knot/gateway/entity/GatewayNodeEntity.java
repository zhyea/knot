package com.knot.gateway.entity;

public class GatewayNodeEntity {
    private Long id;
    private String nodeId;
    private String host;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
