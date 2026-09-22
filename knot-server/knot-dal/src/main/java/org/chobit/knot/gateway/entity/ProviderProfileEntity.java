package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderProfileEntity {
    private Long id;
    private String code;
    private String name;
    private String tag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
