package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ProviderCredentialEntity {

    private Long id;

    private Long providerId;

    private String credentialType;

    private String encryptedConfig;

    private String status;
}
