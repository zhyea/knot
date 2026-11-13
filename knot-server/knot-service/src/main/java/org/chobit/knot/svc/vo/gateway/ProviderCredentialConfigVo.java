package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：供应商凭据（鉴权策略与加密配置）。
 * 字段逐字平移 {@code ProviderCredentialEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 * 含 {@code encryptedConfig} 与 {@code authApplier} 等敏感数据，只在网关进程内被上游鉴权链路消费，
 * 不得作为 HTTP 响应体返回。
 */
public record ProviderCredentialConfigVo(
        Long id,
        Long providerId,
        String providerAccountCode,
        String credentialType,
        String authApplier,
        String encryptedConfig,
        Integer status
) {
}
