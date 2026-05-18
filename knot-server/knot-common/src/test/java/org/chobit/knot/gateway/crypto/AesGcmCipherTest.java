package org.chobit.knot.gateway.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmCipherTest {

    private static final String DEV_KEY = "knot-dev-credential-encryption-key";

    @Test
    void roundTrip() {
        AesGcmCipher cipher = new AesGcmCipher(AesGcmCipher.deriveKey(DEV_KEY));
        String plain = "sk-openai-demo-xxxxx";
        String encrypted = cipher.encrypt(plain);
        assertTrue(AesGcmCipher.isEncrypted(encrypted));
        assertNotEquals(plain, encrypted);
        assertEquals(plain, cipher.decrypt(encrypted));
    }

    @Test
    void legacyPlaintextPassthrough() {
        AesGcmCipher cipher = new AesGcmCipher(AesGcmCipher.deriveKey(DEV_KEY));
        assertEquals("legacy-plain-key", cipher.decrypt("legacy-plain-key"));
    }

}
