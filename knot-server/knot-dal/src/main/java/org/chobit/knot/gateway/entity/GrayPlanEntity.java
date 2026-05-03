package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GrayPlanEntity {
    private Long id;
    private String planName;
    private String targetType;
    private Long targetId;
    private Integer trafficPercent;
    private String stepsJson;
    private LocalDateTime startTime;
    private String status;
}
