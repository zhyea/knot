package org.chobit.knot.gateway.constants.enums;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：ProviderCredentialTypeEnum 是认证类型的单一来源（DB 分类 credential_type 已退役）。
 * requiredFields 同时用于前端动态表单和后端保存校验，改动即意味着两侧行为同时变化。
 */
class ProviderCredentialTypeEnumTest {

    @Test
    void containsFiveTypesWithUniqueCodeAndLabel() {
        ProviderCredentialTypeEnum[] values = ProviderCredentialTypeEnum.values();
        assertEquals(5, values.length);

        Set<String> codes = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (ProviderCredentialTypeEnum type : values) {
            assertTrue(codes.add(type.code()), "duplicated credential type code: " + type.code());
            assertTrue(labels.add(type.label()), "duplicated credential type label: " + type.label());
            assertNotNull(type.requiredFields(), "requiredFields must never be null: " + type.code());
        }
    }

    @Test
    void requiredFieldsMatchCurrentDefinition() {
        assertEquals(java.util.List.of("apiKey"), ProviderCredentialTypeEnum.API_KEY.requiredFields());
        assertEquals(java.util.List.of("accessKey", "secretKey", "region"), ProviderCredentialTypeEnum.AWS_AUTH.requiredFields());
        assertEquals(java.util.List.of("project_id", "region", "account_json"), ProviderCredentialTypeEnum.GEMINI_AUTH.requiredFields());
        assertEquals(java.util.List.of("accessKey", "secretKey"), ProviderCredentialTypeEnum.AK_SK.requiredFields());
        assertTrue(ProviderCredentialTypeEnum.CUSTOM.requiredFields().isEmpty());
    }

    @Test
    void fromCodeAcceptsNewCodesAndLegacyValues() {
        assertEquals(ProviderCredentialTypeEnum.API_KEY, ProviderCredentialTypeEnum.fromCode("api-key"));
        assertEquals(ProviderCredentialTypeEnum.API_KEY, ProviderCredentialTypeEnum.fromCode("API_KEY"));
        assertEquals(ProviderCredentialTypeEnum.CUSTOM, ProviderCredentialTypeEnum.fromCode("TOKEN"));
        assertEquals(ProviderCredentialTypeEnum.API_KEY, ProviderCredentialTypeEnum.fromCode(null));
        assertThrows(IllegalArgumentException.class, () -> ProviderCredentialTypeEnum.fromCode("NOPE"));
    }
}
