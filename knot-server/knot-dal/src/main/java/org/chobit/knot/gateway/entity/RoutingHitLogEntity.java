package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoutingHitLogEntity {
    private Long id;
    private Long ruleId;
    private String fromTarget;
    private String toTarget;
    private String reason;
    private LocalDateTime hitTime;
}
