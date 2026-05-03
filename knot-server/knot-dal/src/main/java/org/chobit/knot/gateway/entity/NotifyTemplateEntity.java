package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class NotifyTemplateEntity {
    private Long id;
    private String code;
    private String name;
    private String channel;
    private String contentTpl;
    private String status;
}
