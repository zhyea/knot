package org.chobit.knot.gateway.vo.provider;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 供应商账户详情：详情查询的响应，同时作为新建与更新的请求体。
 *
 * <p>与列表项 {@link ProviderAccountItem} 分开，是因为列表不需要这里的任何字段，
 * 而写操作又必须能提交认证配置与策略。两者合并成一个 VO 时，
 * 列表就被迫承担解密凭据与查询策略的开销。
 *
 * <p>{@code id / providerName / createdAt / updatedAt} 在新建请求中为 {@code null}。
 */
public record ProviderAccountDetail(Long id, Long providerId, String providerName,
                                    String code, String baseUrl, boolean enabled,
                                    LocalDateTime createdAt, LocalDateTime updatedAt,
                                    String credentialType,
                                    Map<String, Object> authConfig,
                                    RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
