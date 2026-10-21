package org.chobit.knot.gateway.constants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;

/**
 * 供应商认证字段定义常量：{@code ProviderCredentialTypeEnum#requiredFields()} 的唯一数据源。
 *
 * <p>为什么单独一个类维护：字段是**数据**而不是逻辑，且同名字段会被多个认证类型复用
 * （{@code accessKey}/{@code secretKey} 同时属于 AWS_AUTH 与 AK_SK）。散在枚举里改一处就得记得
 * 改另一处；集中到这里后，枚举只保留 code / label 与「类型 → 字段集合」的引用关系。
 *
 * <p>组织方式分两段：先声明单字段常量（{@code *_FIELD}），再声明各认证类型的字段集合
 * （{@code *_FIELDS}）。节点结构（与前端契约一致）：
 * <pre>
 * {"name":"region", "label":"Region", "type":"select", "required":true,
 *  "default":"us-east-1", "placeholder":"...", "options":[{"value":"us-east-1","label":"..."}]}
 * </pre>
 *
 * <p>⚠ {@link JsonNode} 是可变对象：本类的常量**不得被调用方修改**，
 * 对外一律经 {@code ProviderCredentialTypeEnum#requiredFields()} 取深拷贝。
 */
public final class ProviderCredentialFields {

    private ProviderCredentialFields() {
    }

    // ------------------------------------------------------------------ 单字段

    /**
     * apiKey：OpenAI 风格的单一 API Key。
     */
    public static final JsonNode API_KEY_FIELD =
            password("apiKey", "API Key", "sk-...");

    /**
     * accessKey：AWS / 通用 AK，AWS 与 AK&SK 两种认证类型共用同一份定义。
     */
    public static final JsonNode ACCESS_KEY_FIELD =
            text("accessKey", "Access Key", "AKIA...");

    /**
     * secretKey：与 accessKey 配对，密码框。
     */
    public static final JsonNode SECRET_KEY_FIELD =
            password("secretKey", "Secret Key", "与 Access Key 配对");

    /**
     * region：AWS 区域，签名 region 与端点 region 必须一致。
     */
    public static final JsonNode AWS_REGION_FIELD =
            select("region", "Region", "us-east-1",
                    option("us-east-1", "us-east-1 (US East, N. Virginia)"),
                    option("us-east-2", "us-east-2 (US East, Ohio)"),
                    option("us-west-2", "us-west-2 (US West, Oregon)"),
                    option("ap-northeast-1", "ap-northeast-1 (Asia Pacific, Tokyo)"),
                    option("ap-northeast-2", "ap-northeast-2 (Asia Pacific, Seoul)"),
                    option("ap-southeast-1", "ap-southeast-1 (Asia Pacific, Singapore)"),
                    option("ap-southeast-2", "ap-southeast-2 (Asia Pacific, Sydney)"),
                    option("ap-south-1", "ap-south-1 (Asia Pacific, Mumbai)"),
                    option("eu-central-1", "eu-central-1 (Europe, Frankfurt)"),
                    option("eu-west-1", "eu-west-1 (Europe, Ireland)"),
                    option("eu-west-3", "eu-west-3 (Europe, Paris)"),
                    option("sa-east-1", "sa-east-1 (South America, São Paulo)"),
                    option("cn-north-1", "cn-north-1 (China, Beijing)"),
                    option("cn-northwest-1", "cn-northwest-1 (China, Ningxia)"));

    /**
     * endpointType：AWS 端点类型，支持自由输入（下拉仅给常用值）。
     */
    public static final JsonNode ENDPOINT_TYPE_FIELD =
            select("endpointType", "Endpoint Type", "bedrock-runtime",
                    option("bedrock-runtime", "bedrock-runtime"),
                    option("bedrock-mantle", "bedrock-mantle"));

    /**
     * project_id：GCP 项目 ID。
     */
    public static final JsonNode PROJECT_ID_FIELD =
            text("project_id", "Project ID", "GCP 项目 ID");

    /**
     * region：GCP Vertex 区域。
     */
    public static final JsonNode GEMINI_REGION_FIELD =
            select("region", "Region", "us-central1",
                    option("us-central1", "us-central1 (Iowa)"),
                    option("us-east1", "us-east1 (South Carolina)"),
                    option("us-east4", "us-east4 (N. Virginia)"),
                    option("us-west1", "us-west1 (Oregon)"),
                    option("europe-west1", "europe-west1 (Belgium)"),
                    option("europe-west4", "europe-west4 (Netherlands)"),
                    option("asia-northeast1", "asia-northeast1 (Tokyo)"),
                    option("asia-southeast1", "asia-southeast1 (Singapore)"));

    /**
     * account_json：GCP 服务账号密钥，整段 JSON，用文本域。
     */
    public static final JsonNode ACCOUNT_JSON_FIELD =
            textarea("account_json", "服务账号 JSON", "粘贴 GCP 服务账号密钥 JSON");

    // ------------------------------------------------------------ 类型字段集合

    public static final JsonNode API_KEY_FIELDS =
            fields(API_KEY_FIELD);

    public static final JsonNode AWS_AUTH_FIELDS =
            fields(ACCESS_KEY_FIELD, SECRET_KEY_FIELD, AWS_REGION_FIELD, ENDPOINT_TYPE_FIELD);

    public static final JsonNode GEMINI_AUTH_FIELDS =
            fields(PROJECT_ID_FIELD, GEMINI_REGION_FIELD, ACCOUNT_JSON_FIELD);

    public static final JsonNode AK_SK_FIELDS =
            fields(ACCESS_KEY_FIELD, SECRET_KEY_FIELD);

    public static final JsonNode CUSTOM_FIELDS =
            fields();

    /**
     * 字段控件类型；新增类型需前后端同步（前端未知类型按文本输入兜底）。
     */
    private enum FieldType {
        TEXT("text"),
        PASSWORD("password"),
        TEXTAREA("textarea"),
        SELECT("select");

        private final String code;

        FieldType(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    private static ObjectNode text(String name, String label, String placeholder) {
        return withPlaceholder(baseField(name, label, FieldType.TEXT), placeholder);
    }

    private static ObjectNode password(String name, String label, String placeholder) {
        return withPlaceholder(baseField(name, label, FieldType.PASSWORD), placeholder);
    }

    private static ObjectNode textarea(String name, String label, String placeholder) {
        return withPlaceholder(baseField(name, label, FieldType.TEXTAREA), placeholder);
    }

    private static ObjectNode select(String name, String label, String defaultValue, ObjectNode... options) {
        ObjectNode node = baseField(name, label, FieldType.SELECT);
        if (defaultValue != null && !defaultValue.isBlank()) {
            node.put("default", defaultValue);
        }
        ArrayNode optionArray = node.putArray("options");
        Arrays.stream(options).forEach(optionArray::add);
        return node;
    }

    private static ObjectNode baseField(String name, String label, FieldType type) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", name);
        node.put("label", label);
        node.put("type", type.code());
        node.put("required", true);
        return node;
    }

    private static ObjectNode withPlaceholder(ObjectNode node, String placeholder) {
        if (placeholder != null && !placeholder.isBlank()) {
            node.put("placeholder", placeholder);
        }
        return node;
    }

    private static ObjectNode option(String value, String label) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("value", value);
        node.put("label", label);
        return node;
    }

    private static ArrayNode fields(JsonNode... nodes) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        Arrays.stream(nodes).forEach(array::add);
        return array;
    }
}
