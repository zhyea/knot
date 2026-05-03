package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GatewayRequestEntity {
    private Long id;
    private String requestId;
    private Long appId;
    private Long providerId;
    private Long modelId;
    private Long billingRuleId;
    private String status;
    private Integer latencyMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private BigDecimal costAmount;
    private String errorCode;
    private LocalDateTime requestTime;
}
