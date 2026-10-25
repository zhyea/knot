package org.chobit.knot.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderProfileEntity {
    private Long id;
    private String code;
    private String name;
    private String tag;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
