package org.chobit.knot.svc.vo.model;

import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

public record ModelItem(Long id, String modelCode, String upstreamModel, String providerAccountCode, String providerName, String providerCode, String modelType,
                        String version, String basePath, String remark, boolean enabled, String logicalModelCode, String billingRuleCode,
                        RateLimitPolicy rateLimitPolicy, @Valid List<QuotaPolicy> quotaPolicies,
                        List<ModelApiBindingItem> apiBindings,
                        boolean deleted,
                        /** 只读派生：供应商模型编码的引用处数；0 表示未绑定，可改编码 */
                        Long boundCount,
                        /** 创建时间（只读：库侧默认值，请求体传入会被忽略） */
                        LocalDateTime createdAt,
                        /** 更新时间（只读：库侧 ON UPDATE 维护，请求体传入会被忽略） */
                        LocalDateTime updatedAt) {
}
