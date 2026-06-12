package org.chobit.knot.gateway.dto.model;

public record ModelApiBindingDto(Long id,
                                 String protocol,
                                 String apiPath,
                                 String requestAdapter,
                                 String usageExtractor,
                                 String streamUsageExtractor,
                                 boolean enabled,
                                 String remark) {
}
