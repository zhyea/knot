package org.chobit.knot.gateway.adapter.usage;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;

import java.util.Set;

public record UsageExtractorDefinition(String code,
                                       String label,
                                       String description,
                                       String className,
                                       boolean streamSupported,
                                       Set<BillingModeEnum> billingModes) {
}
