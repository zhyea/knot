package org.chobit.knot.svc.dto.model;

import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;

import java.util.List;

public record ModelDto(Long id, String modelCode, String upstreamModel, String providerAccountCode, String providerName, String providerCode, String modelType,
                       String version, String basePath, String remark, boolean enabled, String logicalModelCode, String billingRuleCode,
                       RateLimitPolicy rateLimitPolicy, List<QuotaPolicy> quotaPolicies,
                       List<ModelApiBindingDto> apiBindings,
                       boolean deleted,
                       /**
                        * 只读派生：供应商模型编码的引用处数（路由目标 / 池内条目 / 厂商映射 /
                        * API 协议绑定 / 折扣策略）。未绑定（0）时编码可改，>0 时后端拒绝改码。
                        */
                       Long boundCount) {
}
