package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class NotifyTemplateEntity {
    private Long id;
    private String code;
    private String name;
    private String channel;
    private String contentTpl;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
