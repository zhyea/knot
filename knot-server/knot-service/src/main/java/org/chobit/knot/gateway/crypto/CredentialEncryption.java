package org.chobit.knot.gateway.crypto;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.config.CredentialProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CredentialEncryption {

    private final AesGcmCipher cipher;

    /**
     * Constructs a new instance.
     */
    public CredentialEncryption(CredentialProperties properties) {
        if (properties.isDevDefaultKey()) {
            log.warn("[安全] 凭证加密密钥仍为开发默认值，生产环境必须通过环境变量 KNOT_CREDENTIAL_ENCRYPTION_KEY 注入");
        }
        this.cipher = new AesGcmCipher(AesGcmCipher.deriveKey(properties.getEncryptionKey()));
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String encrypt(String plaintext) {
        return cipher.encrypt(plaintext);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String decrypt(String stored) {
        return cipher.decrypt(stored);
    }
}
