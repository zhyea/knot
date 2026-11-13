package org.chobit.knot.svc.vo.provider;

import java.time.LocalDateTime;

/**
 * 供应商账户列表项：承载列表与账户选择器所需的非敏感字段。
 * 不返回 {@code credentialType / authConfig / rateLimitPolicy / quotaPolicy}，这些字段由详情接口
 * {@code GET /api/provider-accounts/{id}} 提供；新增字段前须确认列表页会渲染它。
 * {@code providerCode} 是例外：模型表单选择账户后用它（供应商主数据 code）联动过滤计费规则。
 */
public record ProviderAccountItem(Long id, String providerCode, String code, String providerName, String type,
                                  String baseUrl, LocalDateTime createdAt, LocalDateTime updatedAt,
                                  boolean enabled) {
}
