package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knot.credential")
public class CredentialProperties {

    /**
     * 鍑瘉鍔犲瘑鍙ｄ护锛堢粡 SHA-256 娲剧敓涓?AES-256 瀵嗛挜锛夈€傜敓浜х幆澧冭閫氳繃鐜鍙橀噺瑕嗙洊銆?
     */
    private String encryptionKey = "knot-dev-credential-encryption-key";

    /**
     * Returns the requested value. Executes the public operation.
     */
    public String getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
