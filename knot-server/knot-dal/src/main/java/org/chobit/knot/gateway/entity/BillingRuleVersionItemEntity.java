package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillingRuleVersionItemEntity {
    private Long id;
    private Long versionId;
    private String itemType;
    private String unit;
    private Integer unitSize;
    private BigDecimal unitPrice;
    private String metadataJson;
}
