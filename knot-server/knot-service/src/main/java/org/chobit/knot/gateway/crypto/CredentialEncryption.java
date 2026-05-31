package org.chobit.knot.gateway.crypto;

import org.chobit.knot.gateway.config.CredentialProperties;
import org.springframework.stereotype.Component;

@Component
public class CredentialEncryption {

    private final AesGcmCipher cipher;

    /**
     * Constructs a new instance.
     */
    public CredentialEncryption(CredentialProperties properties) {
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

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public static boolean isEncrypted(String value) {
        return AesGcmCipher.isEncrypted(value);
    }
}
