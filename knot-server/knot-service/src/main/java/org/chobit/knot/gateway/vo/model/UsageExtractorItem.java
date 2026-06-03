package org.chobit.knot.gateway.vo.model;

public record UsageExtractorItem(String code,
                                 String label,
                                 String className,
                                 boolean streamSupported) {
}
