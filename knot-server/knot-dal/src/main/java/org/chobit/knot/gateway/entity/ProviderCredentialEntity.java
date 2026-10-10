package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ProviderCredentialEntity {

    private Long id;

    /** 由 join kb_provider_accounts 派生（pa.id），不落库 */
    private Long providerId;

    /** 落库列：供应商账户业务码（kb_provider_accounts.code），非主键 id */
    private String providerAccountCode;

    private String credentialType;

    /** 鉴权策略编码（UpstreamAuthApplier code），明文列 */
    private String authApplier;

    private String encryptedConfig;

    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
