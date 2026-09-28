package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AppCredentialEntity {
    private Long id;
    /** 所属应用业务码（kb_apps.app_id），与 AppEntity.appId 同义 */
    private String appId;
    private String appKey;
    private String appSecretHash;
    private String status;
}
