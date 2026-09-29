package org.chobit.knot.gateway.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 {@link JsonKit#toMaskedMap} 的字段级脱敏契约：
 * <ul>
 *   <li>record 组件上的 {@link Sensitive} 注解字段被脱敏；</li>
 *   <li>类（如 Lombok {@code @Data} 实体）上名为 {@code passwordHash} 的字段被脱敏（注解不可达时的名字兜底）；</li>
 *   <li>{@link JsonKit#toMap}（普通 mapper）不脱敏，证明脱敏作用域仅限审计快照。</li>
 * </ul>
 */
class JsonKitMaskingTest {

    /** 长度超过阈值的明文，用于断言脱敏后不再含完整原文。 */
    private static final String RAW_SECRET = "abcdefghij12345";

    @Test
    void recordSensitiveFieldIsMasked() {
        Map<String, Object> masked = JsonKit.toMaskedMap(new SecretHolder(RAW_SECRET));
        Object value = masked.get("secretKey");
        assertTrue(value instanceof String, "secretKey 应被序列化为字符串");
        String maskedValue = (String) value;
        assertFalse(maskedValue.contains(RAW_SECRET), "脱敏后不得包含完整明文: " + maskedValue);
        assertEquals(SensitiveSerializer.mask(RAW_SECRET), maskedValue);
    }

    @Test
    void entityPasswordHashIsMaskedByNameFallback() {
        Map<String, Object> masked = JsonKit.toMaskedMap(new CredentialHolder(RAW_SECRET));
        Object value = masked.get("passwordHash");
        assertTrue(value instanceof String);
        String maskedValue = (String) value;
        assertFalse(maskedValue.contains(RAW_SECRET), "passwordHash 不得明文落盘: " + maskedValue);
        assertEquals(SensitiveSerializer.mask(RAW_SECRET), maskedValue);
    }

    @Test
    void normalFieldIsNotMasked() {
        Map<String, Object> masked = JsonKit.toMaskedMap(new PlainHolder("hello"));
        assertEquals("hello", masked.get("name"));
    }

    @Test
    void toMapDoesNotMask() {
        // 普通 mapper 不挂 MaskingModule，证明脱敏不会泄漏到正常序列化路径。
        Map<String, Object> plain = JsonKit.toMap(new SecretHolder(RAW_SECRET));
        assertEquals(RAW_SECRET, plain.get("secretKey"));
    }

    record SecretHolder(@Sensitive String secretKey) {
    }

    /** 模拟 Lombok {@code @Data} 实体：字段 + getter，无 @Sensitive 注解（验证名字兜底）。 */
    static class CredentialHolder {
        private final String passwordHash;

        CredentialHolder(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        public String getPasswordHash() {
            return passwordHash;
        }
    }

    record PlainHolder(String name) {
    }
}
