package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AppEntity {
    private Long id;
    private String appId;
    private String name;
    private Long ownerUserId;
    private String status;
    private String rateLimitJson;
    private String quotaJson;
}
