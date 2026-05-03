package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AppCredentialEntity {
    private Long id;
    private Long appId;
    private String appKey;
    private String appSecretHash;
    private String status;
}
