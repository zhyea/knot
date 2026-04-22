package com.knot.gateway.entity;

public class UserEntity {
    private Long id;
    private String username;
    private String realName;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
