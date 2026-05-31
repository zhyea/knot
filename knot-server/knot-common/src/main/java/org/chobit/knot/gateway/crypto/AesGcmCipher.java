package org.chobit.knot.gateway.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Executes the public operation. Executes the public operation.
 */
/**
 * AES-256-GCM 瀵圭О鍔犺В瀵嗭紝瀵嗘枃鏍煎紡锛歿@code ENC:} + Base64(IV[12] + ciphertext+tag)銆?
 */
public final class AesGcmCipher {

    public static final String PREFIX = "ENC:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private final SecretKey secretKey;

    /**
     * Constructs a new instance.
     */
    public AesGcmCipher(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static byte[] deriveKey(String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalArgumentException("encryption passphrase must not be blank");
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(passphrase.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("credential encrypt failed", e);
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    /**
     * 瑙ｅ瘑锛涢潪 {@link #PREFIX} 鏍煎紡瑙嗕负鍘嗗彶鏄庢枃锛堝吋瀹硅縼绉伙級銆?
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (combined.length <= IV_LEN) {
                throw new IllegalArgumentException("invalid encrypted credential");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LEN);
            byte[] cipherBytes = Arrays.copyOfRange(combined, IV_LEN, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("credential decrypt failed", e);
        }
    }
}
