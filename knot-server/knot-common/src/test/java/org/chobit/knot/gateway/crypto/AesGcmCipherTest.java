package org.chobit.knot.gateway.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmCipherTest {

    private static final String DEV_KEY = "knot-dev-credential-encryption-key";

    @Test
    void roundTrip() {
        AesGcmCipher cipher = new AesGcmCipher(DEV_KEY);
        String plain = "sk-openai-demo-xxxxx";
        String encrypted = cipher.encrypt(plain);
        assertTrue(AesGcmCipher.isEncrypted(encrypted));
        // 新写入统一走 v2（PBKDF2 + 随机盐）格式
        assertTrue(encrypted.startsWith(AesGcmCipher.PREFIX_V2));
        assertNotEquals(plain, encrypted);
        assertEquals(plain, cipher.decrypt(encrypted));
    }

    /**
     * 只接受 v2 格式：未加密明文与历史 v1 密文一律抛错，不透传——
     * 钉死「不把未加密串当密钥使用」的红线。
     */
    @Test
    void rejectsPlaintextAndLegacyCiphertext() {
        AesGcmCipher cipher = new AesGcmCipher(DEV_KEY);
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("legacy-plain-key"));
        assertThrows(IllegalArgumentException.class,
                () -> cipher.decrypt(AesGcmCipher.PREFIX + "bGVnYWN5LXYxLWNpcGhlcnRleHQ="));
    }
}
