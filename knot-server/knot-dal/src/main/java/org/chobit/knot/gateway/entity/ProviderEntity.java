package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderEntity {
    private Long id;
    private String code;
    private String name;
    private String providerType;
    private String baseUrl;
    private String status;
    private String rateLimitJson;
    private String quotaJson;
    private Long lastOperatorId;
    private String lastOperatorName;
    private LocalDateTime updatedAt;
}
