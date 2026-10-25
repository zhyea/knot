package org.chobit.knot.svc.dto.provider;

import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ProviderAccountDto(Long id, String providerCode, String providerName,
                                 String code, String type, String baseUrl, boolean enabled,
                                 LocalDateTime createdAt, LocalDateTime updatedAt,
                                 String credentialType,
                                 String authApplier,
                                 Map<String, Object> authConfig,
                                 RateLimitPolicy rateLimitPolicy, List<QuotaPolicy> quotaPolicies,
                                 /**
                                  * 只读派生：按本账户编码绑定的供应商模型数。未绑定（0）时编码可改，
                                  * >0 时后端拒绝改码、前端置灰输入框。
                                  */
                                 Long boundCount) {
}
