package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knot.credential")
public class CredentialProperties {

    /**
     * 凭证加密口令，经 SHA-256 派生为 AES-256 密钥。
     * 生产环境应通过环境变量或外部配置覆盖。
     */
    private String encryptionKey = "knot-dev-credential-encryption-key";

    /**
     * Returns the configured encryption key.
     */
    public String getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * Updates the configured encryption key.
     */
    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
