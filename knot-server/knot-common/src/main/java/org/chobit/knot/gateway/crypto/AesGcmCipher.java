package org.chobit.knot.gateway.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AES-256-GCM 对称加解密工具。
 *
 * <p>密文格式唯一：{@code ENC:v2:} + Base64(salt[16] + IV[12] + ciphertext + tag)，
 * 密钥由 {@code PBKDF2WithHmacSHA256(passphrase, salt)} 派生。随机盐随每条密文存储，
 * 同一口令不同盐派生出不同密钥，抗彩虹表 / 预计算；迭代次数取 OWASP 推荐值。
 *
 * <p>解密只接受 v2 格式：非 {@link #PREFIX_V2} 前缀的值（含历史 v1 格式与未加密明文）
 * 一律抛错，不静默透传——宁可启动失败，也不把未加密串当密钥使用。</p>
 */
public final class AesGcmCipher {

    public static final String PREFIX = "ENC:";
    public static final String PREFIX_V2 = "ENC:v2:";
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String PBKDF2 = "PBKDF2WithHmacSHA256";

    private final String passphrase;
    private final SecureRandom random = new SecureRandom();
    /**
     * 派生密钥缓存：按 base64(salt) 缓存。规模等于「不同盐的数量」（即不同凭据数），
     * 天然有界；避免每次取凭据都跑一次 PBKDF2。
     */
    private final Map<String, SecretKey> keyCache = new ConcurrentHashMap<>(64);

    /**
     * Constructs a new instance from the encryption passphrase.
     */
    public AesGcmCipher(String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalArgumentException("encryption passphrase must not be blank");
        }
        this.passphrase = passphrase;
    }

    /**
     * 判断给定值是否已被加密（v2 前缀）。
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 加密明文，统一产出 v2 格式（随机盐 + PBKDF2 派生密钥）。
     *
     * <p>空 / 空白 / 已是密文的值原样返回，避免重复加密。</p>
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            byte[] salt = new byte[SALT_LEN];
            // GCM 的 12 字节 IV 不需要「强」随机；盐与 IV 都用非阻塞实现，避免部分平台 /dev/random 阻塞。
            random.nextBytes(salt);
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            SecretKey key = v2Key(salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[salt.length + iv.length + encrypted.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(encrypted, 0, combined, salt.length + iv.length, encrypted.length);
            return PREFIX_V2 + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("credential encrypt failed", e);
        }
    }

    /**
     * 解密存储值：只支持 v2（PBKDF2）格式；非 v2 前缀的值直接抛错，不透传。
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.startsWith(PREFIX_V2)) {
            throw new IllegalArgumentException(
                    "unsupported credential ciphertext, expected " + PREFIX_V2 + " prefix");
        }
        try {
            return decryptV2(stored);
        } catch (Exception e) {
            throw new IllegalStateException("credential decrypt failed", e);
        }
    }

    private String decryptV2(String stored) {
        byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX_V2.length()));
        if (combined.length <= SALT_LEN + IV_LEN) {
            throw new IllegalArgumentException("invalid encrypted credential");
        }
        byte[] salt = Arrays.copyOfRange(combined, 0, SALT_LEN);
        byte[] iv = Arrays.copyOfRange(combined, SALT_LEN, SALT_LEN + IV_LEN);
        byte[] cipherBytes = Arrays.copyOfRange(combined, SALT_LEN + IV_LEN, combined.length);
        return gcmDecrypt(v2Key(salt), iv, cipherBytes);
    }

    private static String gcmDecrypt(SecretKey key, byte[] iv, byte[] cipherBytes) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("credential decrypt failed", e);
        }
    }

    private SecretKey v2Key(byte[] salt) {
        String cacheKey = Base64.getEncoder().encodeToString(salt);
        return keyCache.computeIfAbsent(cacheKey, k -> derivePbkdf2(salt));
    }

    private SecretKey derivePbkdf2(byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2);
            byte[] raw = factory.generateSecret(
                    new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
            ).getEncoded();
            return new SecretKeySpec(raw, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 key derivation failed", e);
        }
    }
}
