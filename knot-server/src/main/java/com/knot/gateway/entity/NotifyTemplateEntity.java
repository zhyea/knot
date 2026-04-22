package com.knot.gateway.entity;

public class NotifyTemplateEntity {
    private Long id;
    private String code;
    private String name;
    private String channel;
    private String contentTpl;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getContentTpl() { return contentTpl; }
    public void setContentTpl(String contentTpl) { this.contentTpl = contentTpl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
