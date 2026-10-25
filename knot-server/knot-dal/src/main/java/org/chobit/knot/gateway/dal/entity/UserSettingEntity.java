package org.chobit.knot.gateway.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSettingEntity {
    private Long id;
    private Long userId;
    private String settingKey;
    private String settingValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
