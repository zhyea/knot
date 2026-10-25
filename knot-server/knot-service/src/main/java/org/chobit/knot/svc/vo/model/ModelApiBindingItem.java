package org.chobit.knot.svc.vo.model;

public record ModelApiBindingItem(Long id,
                                  String protocol,
                                  String apiPath,
                                  String requestAdapter,
                                  String usageExtractor,
                                  String streamUsageExtractor,
                                  boolean enabled,
                                  String remark) {
}
