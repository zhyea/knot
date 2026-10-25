package org.chobit.knot.adapter.usage;

import org.chobit.knot.common.constants.enums.BillingModeEnum;

import java.util.Set;

public record UsageExtractorDefinition(String code,
                                       String label,
                                       String description,
                                       String className,
                                       boolean streamSupported,
                                       Set<BillingModeEnum> billingModes) {
}
