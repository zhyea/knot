package org.chobit.knot.gateway.vo.model;

import java.util.List;

public record UsageExtractorItem(String code,
                                 String label,
                                 String description,
                                 String className,
                                 boolean streamSupported,
                                 List<String> billingModes) {
}
