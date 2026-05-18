package org.chobit.knot.gateway.crypto;

import org.chobit.knot.gateway.config.CredentialProperties;
import org.springframework.stereotype.Component;

@Component
public class CredentialEncryption {

    private final AesGcmCipher cipher;

    public CredentialEncryption(CredentialProperties properties) {
        this.cipher = new AesGcmCipher(AesGcmCipher.deriveKey(properties.getEncryptionKey()));
    }

    public String encrypt(String plaintext) {
        return cipher.encrypt(plaintext);
    }

    public String decrypt(String stored) {
        return cipher.decrypt(stored);
    }

    public static boolean isEncrypted(String value) {
        return AesGcmCipher.isEncrypted(value);
    }
}
