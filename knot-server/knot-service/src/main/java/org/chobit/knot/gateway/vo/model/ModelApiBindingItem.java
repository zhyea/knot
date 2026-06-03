package org.chobit.knot.gateway.vo.model;

public record ModelApiBindingItem(Long id,
                                  String protocol,
                                  String apiPath,
                                  String usageExtractor,
                                  String streamUsageExtractor,
                                  boolean enabled,
                                  String remark) {
}
