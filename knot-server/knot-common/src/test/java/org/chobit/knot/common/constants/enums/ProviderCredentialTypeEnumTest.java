package org.chobit.knot.common.constants.enums;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.chobit.knot.common.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：ProviderCredentialTypeEnum 是认证类型的单一来源（DB 分类 credential_type 已退役）。
 * requiredFields 是字段定义数组（JsonNode），同时驱动前端表单渲染（label/type/options/default）
 * 与后端保存校验（required=true 的 name），改动即意味着两侧行为同时变化。
 */
class ProviderCredentialTypeEnumTest {

    /** 字段节点允许出现的键，多余键说明约定被悄悄扩展，前端未必跟得上 */
    private static final Set<String> ALLOWED_KEYS =
            Set.of("name", "label", "type", "required", "placeholder", "default", "options");

    private static final Set<String> ALLOWED_TYPES =
            Set.of("text", "password", "textarea", "select");

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
            assertTrue(type.requiredFields().isArray(), "requiredFields 必须是数组: " + type.code());
        }
    }

    @Test
    void requiredFieldsNodesFollowFieldDefinitionContract() {
        for (ProviderCredentialTypeEnum type : ProviderCredentialTypeEnum.values()) {
            Set<String> names = new HashSet<>();
            for (JsonNode field : type.requiredFields()) {
                String name = field.path("name").asText();
                assertFalse(name.isBlank(), "字段缺少 name: " + type.code());
                assertTrue(names.add(name), "字段名重复: " + type.code() + "/" + name);
                assertFalse(field.path("label").asText().isBlank(), "字段缺少 label: " + name);
                assertTrue(ALLOWED_TYPES.contains(field.path("type").asText()),
                        "未知控件类型: " + name + " -> " + field.path("type").asText());
                assertTrue(field.path("required").isBoolean(), "required 必须显式声明: " + name);
                field.fieldNames().forEachRemaining(key ->
                        assertTrue(ALLOWED_KEYS.contains(key), "未知字段键: " + name + "." + key));
            }
        }
    }

    @Test
    void requiredFieldsMatchCurrentDefinition() {
        assertEquals(List.of("apiKey"), ProviderCredentialTypeEnum.API_KEY.requiredFieldNames());
        assertEquals(List.of("accessKey", "secretKey", "region", "endpointType"), ProviderCredentialTypeEnum.AWS_AUTH.requiredFieldNames());
        assertEquals(List.of("project_id", "region", "account_json"), ProviderCredentialTypeEnum.GEMINI_AUTH.requiredFieldNames());
        assertEquals(List.of("accessKey", "secretKey"), ProviderCredentialTypeEnum.AK_SK.requiredFieldNames());
        assertTrue(ProviderCredentialTypeEnum.CUSTOM.requiredFields().isEmpty());
        assertTrue(ProviderCredentialTypeEnum.CUSTOM.requiredFieldNames().isEmpty());
    }

    @Test
    void regionIsSelectWithDefaultInOptions() {
        assertRegionSelect(ProviderCredentialTypeEnum.AWS_AUTH, "us-east-1");
        assertRegionSelect(ProviderCredentialTypeEnum.GEMINI_AUTH, "us-central1");
        assertTrue(optionValues(fieldOf(ProviderCredentialTypeEnum.AWS_AUTH, "region")).contains("cn-north-1"),
                "AWS region 选项需覆盖中国区");
    }

    @Test
    void endpointTypeIsSelectWithRuntimeAndMantle() {
        JsonNode endpointType = fieldOf(ProviderCredentialTypeEnum.AWS_AUTH, "endpointType");
        assertEquals("select", endpointType.path("type").asText(), "endpointType 必须是下拉");
        assertTrue(endpointType.path("required").asBoolean(), "endpointType 是必填字段");
        assertEquals("bedrock-runtime", endpointType.path("default").asText());
        assertEquals(List.of("bedrock-runtime", "bedrock-mantle"), optionValues(endpointType));
    }

    @Test
    void requiredFieldsReturnsDefensiveCopy() {
        // 字段定义常量集中在 ProviderCredentialFields 且被多个类型共享，返回值必须是拷贝
        ArrayNode copy = (ArrayNode) ProviderCredentialTypeEnum.API_KEY.requiredFields();
        copy.removeAll();
        assertEquals(1, ProviderCredentialTypeEnum.API_KEY.requiredFields().size(),
                "改动返回值不得污染共享常量");
    }

    @Test
    void sharedFieldDefinitionsStayIdenticalAcrossTypes() {
        // accessKey / secretKey 由 AWS_AUTH 与 AK_SK 共用同一份常量，标签口径不能漂移
        assertEquals(fieldOf(ProviderCredentialTypeEnum.AWS_AUTH, "accessKey"),
                fieldOf(ProviderCredentialTypeEnum.AK_SK, "accessKey"));
        assertEquals(fieldOf(ProviderCredentialTypeEnum.AWS_AUTH, "secretKey"),
                fieldOf(ProviderCredentialTypeEnum.AK_SK, "secretKey"));
    }

    @Test
    void passwordTypeMarksSecretFields() {
        // 密码框由后端 type 直接下发，前端不再按字段名硬编码猜测
        assertEquals("password", fieldOf(ProviderCredentialTypeEnum.API_KEY, "apiKey").path("type").asText());
        assertEquals("password", fieldOf(ProviderCredentialTypeEnum.AWS_AUTH, "secretKey").path("type").asText());
        assertEquals("textarea", fieldOf(ProviderCredentialTypeEnum.GEMINI_AUTH, "account_json").path("type").asText());
    }

    @Test
    void requiredFieldsSurviveJsonRoundTrip() {
        // 结构原样下发到 /api/provider-accounts/credential-types，序列化必须无损
        for (ProviderCredentialTypeEnum type : ProviderCredentialTypeEnum.values()) {
            JsonNode original = type.requiredFields();
            assertEquals(original, JsonKit.parse(JsonKit.toJson(original)),
                    "序列化后结构与定义不一致: " + type.code());
        }
    }

    @Test
    void fromCodeAcceptsPersistedCodesOnly() {
        assertEquals(ProviderCredentialTypeEnum.API_KEY, ProviderCredentialTypeEnum.fromCode("api-key"));
        assertEquals(ProviderCredentialTypeEnum.API_KEY, ProviderCredentialTypeEnum.fromCode("API_KEY"));
        assertThrows(IllegalArgumentException.class, () -> ProviderCredentialTypeEnum.fromCode(null));
        assertThrows(IllegalArgumentException.class, () -> ProviderCredentialTypeEnum.fromCode("  "));
        assertThrows(IllegalArgumentException.class, () -> ProviderCredentialTypeEnum.fromCode("TOKEN"));
        assertThrows(IllegalArgumentException.class, () -> ProviderCredentialTypeEnum.fromCode("NOPE"));
    }

    private static void assertRegionSelect(ProviderCredentialTypeEnum type, String expectedDefault) {
        JsonNode region = fieldOf(type, "region");
        assertEquals("select", region.path("type").asText(), type.code() + " 的 region 必须是下拉");
        assertEquals(expectedDefault, region.path("default").asText(), type.code() + " 的 region 默认值");
        List<String> values = optionValues(region);
        assertFalse(values.isEmpty(), type.code() + " 的 region 选项不能为空");
        assertTrue(values.contains(expectedDefault), type.code() + " 的默认值必须在选项内");
        region.path("options").forEach(option -> {
            assertFalse(option.path("value").asText().isBlank());
            assertFalse(option.path("label").asText().isBlank());
        });
    }

    private static JsonNode fieldOf(ProviderCredentialTypeEnum type, String name) {
        for (JsonNode field : type.requiredFields()) {
            if (name.equals(field.path("name").asText())) {
                return field;
            }
        }
        throw new AssertionError(type.code() + " 缺少字段 " + name);
    }

    private static List<String> optionValues(JsonNode field) {
        List<String> values = new ArrayList<>();
        field.path("options").forEach(option -> values.add(option.path("value").asText()));
        return values;
    }
}
