package org.chobit.knot.svc.vo.model;

public record ModelApiBindingItem(Long id,
                                  String protocol,
                                  String apiPath,
                                  String requestAdapter,
                                  String usageExtractor,
                                  String streamUsageExtractor,
                                  boolean enabled,
                                  String remark,
                                  /** 异步任务档案编码；{@code null} 表示同步。列表页据此打 [任务] 标记 */
                                  String asyncProfileCode) {
}
