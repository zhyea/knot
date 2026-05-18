package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderCredentialEntity {
    private Long id;
    private Long providerId;
    private String credentialType;
    private String encryptedKey;
    private String encryptedSecret;
    private String tokenValue;
    private LocalDateTime expireAt;
    private String status;
}
