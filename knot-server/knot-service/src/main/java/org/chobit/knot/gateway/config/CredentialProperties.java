package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knot.credential")
public class CredentialProperties {

    /**
     * 凭证加密口令（经 SHA-256 派生为 AES-256 密钥）。生产环境请通过环境变量覆盖。
     */
    private String encryptionKey = "knot-dev-credential-encryption-key";

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
