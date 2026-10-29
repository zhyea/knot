package org.chobit.knot.svc.dto.model;

public record ModelApiBindingDto(Long id,
                                 String protocol,
                                 String apiPath,
                                 String requestAdapter,
                                 String usageExtractor,
                                 String streamUsageExtractor,
                                 boolean enabled,
                                 String remark,
                                 /**
                                  * 异步任务档案编码（{@code kb_async_task_profiles.profile_code}）；
                                  * {@code null} = 同步（提交即终态）。
                                  *
                                  * <p>⚠ 空串必须在写入前折成 null：{@code ''} 会被当成一个不存在的档案编码，
                                  * 报错信息却是「档案不存在」，排查方向直接偏掉。</p>
                                  */
                                 String asyncProfileCode) {
}
