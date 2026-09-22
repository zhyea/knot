package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ProviderEntity {
    private Long id;
    private Long providerId;
    private String providerName;
    private String code;
    private String name;
    private String providerType;
    private String status;
}
