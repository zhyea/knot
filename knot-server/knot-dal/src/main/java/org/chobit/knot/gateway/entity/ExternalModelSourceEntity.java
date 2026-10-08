package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalModelSourceEntity {
    private Long id;
    private String sourceCode;
    private String sourceName;
    private String sourceUrl;
    private String apiUrl;
    private String sourceType;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
