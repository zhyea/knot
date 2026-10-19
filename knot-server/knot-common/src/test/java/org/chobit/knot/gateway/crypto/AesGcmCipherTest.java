package org.chobit.knot.gateway.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void legacyPlaintextPassthrough() {
        AesGcmCipher cipher = new AesGcmCipher(DEV_KEY);
        assertEquals("legacy-plain-key", cipher.decrypt("legacy-plain-key"));
    }

    /**
     * 升级后仍能解密存量 v1 密文（SHA-256 派生 + GCM）：手动复刻 v1 格式，
     * 防止 legacy 解密路径被误删导致存量凭据无法读取。
     */
    @Test
    void legacyV1CiphertextStillDecrypts() throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(AesGcmCipher.deriveKey(DEV_KEY), "AES"),
                new GCMParameterSpec(128, iv));
        byte[] ct = cipher.doFinal("old-secret-value".getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ct, 0, combined, iv.length, ct.length);
        String legacy = AesGcmCipher.PREFIX + Base64.getEncoder().encodeToString(combined);

        AesGcmCipher underTest = new AesGcmCipher(DEV_KEY);
        assertTrue(legacy.startsWith(AesGcmCipher.PREFIX));
        assertFalse(legacy.startsWith(AesGcmCipher.PREFIX_V2));
        assertEquals("old-secret-value", underTest.decrypt(legacy));
    }
}
