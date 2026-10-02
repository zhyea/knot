package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ProviderCredentialEntity {

    private Long id;

    private Long providerId;

    private String credentialType;

    /** 鉴权策略编码（UpstreamAuthApplier code），明文列 */
    private String authApplier;

    private String encryptedConfig;

    private String status;
}
