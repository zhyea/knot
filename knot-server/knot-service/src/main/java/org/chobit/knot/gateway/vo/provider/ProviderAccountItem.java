package org.chobit.knot.gateway.vo.provider;

import java.time.LocalDateTime;

/**
 * 供应商账户列表项：只承载列表表格真正渲染的字段。
 *
 * <p>列表刻意<b>不返回</b> {@code providerId / baseUrl / credentialType / authConfig /
 * rateLimitPolicy / quotaPolicy}：列表页一个都不展示，它们全部由详情接口
 * {@code GET /api/provider-accounts/{id}} 提供。
 *
 * <p>这些字段曾一并出现在列表里，代价是每翻一页都要逐行解密凭据并反序列化认证配置，
 * 且会把明文 apiKey 送到浏览器（2026-09-23 的凭据解析故障正是被这条路径放大）。
 * 新增字段前请先确认列表页确实会渲染它。
 */
public record ProviderAccountItem(Long id, String code, String providerName,
                                  LocalDateTime createdAt, LocalDateTime updatedAt,
                                  boolean enabled) {
}
