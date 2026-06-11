package org.chobit.knot.gateway.usage;

public record UsageExtractorDefinition(String code,
                                       String label,
                                       String className,
                                       boolean streamSupported) {
}
