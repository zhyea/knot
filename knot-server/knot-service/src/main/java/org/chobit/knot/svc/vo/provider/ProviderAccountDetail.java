package org.chobit.knot.svc.vo.provider;

import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

/**
 * 供应商账户详情：详情查询的响应，同时作为新建与更新的请求体，与列表项 {@link ProviderAccountItem} 分开。
 * 写操作经此提交认证配置与策略；{@code id / providerName / createdAt / updatedAt} 在新建请求中为 {@code null}。
 */
public record ProviderAccountDetail(Long id, String providerCode, String providerName,
                                    String code, String baseUrl, boolean enabled,
                                    LocalDateTime createdAt, LocalDateTime updatedAt,
                                    String credentialType,
                                    String authApplier,
                                    Map<String, Object> authConfig,
                                    RateLimitPolicy rateLimitPolicy, @Valid List<QuotaPolicy> quotaPolicies,
                                    /** 只读派生：账户编码的引用处数（挂载的供应商模型数）；0 表示可改编码 */
                                    Long boundCount) {
}
