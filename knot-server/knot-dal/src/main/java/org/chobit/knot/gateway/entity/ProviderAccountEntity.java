package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderAccountEntity {
    private Long id;
    private Long providerId;
    private String providerName;
    private String code;
    private String name;
    private String providerCode;
    private String baseUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
