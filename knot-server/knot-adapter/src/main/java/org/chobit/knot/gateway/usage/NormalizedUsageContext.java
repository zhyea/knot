package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.BillingUsage;

import java.util.LinkedHashMap;
import java.util.Map;

public record NormalizedUsageContext(BillingRuleEntity rule,
                                     BillingUsage usage,
                                     Map<String, Object> requestBody) {

    public NormalizedUsageContext(BillingRuleEntity rule,
                                  BillingUsage usage,
                                  Map<String, Object> requestBody) {
        this.rule = rule;
        this.usage = usage;
        this.requestBody = requestBody == null ? Map.of() : new LinkedHashMap<>(requestBody);
    }
}
