package org.chobit.knot.gateway.vo.model;

public record ModelApiBindingItem(Long id,
                                  String protocol,
                                  String baseUrl,
                                  String apiPath,
                                  String requestAdapter,
                                  String usageExtractor,
                                  String streamUsageExtractor,
                                  boolean enabled,
                                  String remark) {
}
