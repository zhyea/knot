package org.chobit.knot.gateway.adapter.support.aws;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SigV4 签名器回归测试。
 *
 * <p><b>基准不是我手算的</b>：期望值由 AWS 官方 SDK（botocore 1.43.116）对同一组输入
 * 离线算出，生成脚本 {@code .workbuddy/audit/aws_sigv4_reference.py}、
 * 输出快照 {@code audit/aws-sigv4-reference-2026-10-22.json}。签名错一位全站 403，
 * 必须拿官方实现压着跑。</p>
 */
class AwsSigV4SupportTest {

    private static final String ACCESS = "AKIAEXAMPLEAWSBEDROCK";
    private static final String SECRET = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
    /** botocore 基准快照里的时间戳，固定后两边才有可比性。 */
    private static final String AMZ_DATE = "20261021T170700Z";
    private static final String DATE_STAMP = "20261021";

    private static final String CONVERSE_BODY =
            "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"hi\"}]}],\"inferenceConfig\":{\"maxTokens\":1024}}";
    private static final String CONVERSE_MODEL_ID = "anthropic.claude-sonnet-4-20250514-v1:0";

    @Test
    void shouldMatchAwsSdkReferenceForBedrockRuntimeConverse() {
        String body = CONVERSE_BODY;
        String canonicalUri = "/model/" + AwsSigV4Support.uriEncode(CONVERSE_MODEL_ID) + "/converse";

        assertEquals("62442447710c345aed6094cae7797069c59fea4459d491b1a39581c0242b27e8",
                AwsSigV4Support.payloadHash(body.getBytes(StandardCharsets.UTF_8)),
                "请求体哈希必须与 AWS SDK 一致");

        assertEquals("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLEAWSBEDROCK/20261021/us-east-1/bedrock/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=d38947ea1ef4b10033ef1adfe6b3c7d1e2b4468858cc585cef71a2075d706152",
                sign("POST", canonicalUri, "bedrock-runtime.us-east-1.amazonaws.com", body, "us-east-1", "bedrock"));
    }

    @Test
    void shouldMatchAwsSdkReferenceForBedrockMantle() {
        String body = "{\"model\":\"openai.gpt-5.6-luna\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        assertEquals("5fbb1be21152a8091cb80b30dca7c251c0ea609de086ca34db641eb5839b0616",
                AwsSigV4Support.payloadHash(body.getBytes(StandardCharsets.UTF_8)));

        // mantle 的 service 名是 bedrock-mantle（不是 bedrock）——基准快照正是按此签的
        assertEquals("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLEAWSBEDROCK/20261021/us-east-1/bedrock-mantle/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=baa28e4e21277faa4420f02442d07bd8f6b1dcd90f1331bc5bf3b9c2f88c46e4",
                sign("POST", "/v1/chat/completions", "bedrock-mantle.us-east-1.api.aws", body, "us-east-1", "bedrock-mantle"));
    }

    @Test
    void shouldMatchAwsSdkReferenceForChinaRegion() {
        String body = "{\"messages\":[]}";
        String canonicalUri = "/model/" + AwsSigV4Support.uriEncode(CONVERSE_MODEL_ID) + "/converse";

        assertEquals("5e4ce7b36ba37b78a5d5f9fd08e6b7b54ba6879d651aa46ec9e1d6fa24ebe30a",
                AwsSigV4Support.payloadHash(body.getBytes(StandardCharsets.UTF_8)));

        assertEquals("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLEAWSBEDROCK/20261021/cn-north-1/bedrock/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=48cdc36342258f9fd03e459b264dd09b8093ab741fa662206e3d0b6a089d39fb",
                sign("POST", canonicalUri, "bedrock-runtime.cn-north-1.amazonaws.com.cn", body, "cn-north-1", "bedrock"));
    }

    @Test
    void emptyPayloadHashShouldBeAwsConstant() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                AwsSigV4Support.payloadHash(new byte[0]));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                AwsSigV4Support.payloadHash((String) null));
    }

    @Test
    void signatureShouldChangeWhenRegionServiceOrBodyChanges() {
        String canonicalUri = "/model/" + AwsSigV4Support.uriEncode(CONVERSE_MODEL_ID) + "/converse";
        String baseline = sign("POST", canonicalUri, "bedrock-runtime.us-east-1.amazonaws.com",
                CONVERSE_BODY, "us-east-1", "bedrock");

        assertNotEquals(baseline, sign("POST", canonicalUri, "bedrock-runtime.us-east-1.amazonaws.com",
                CONVERSE_BODY, "eu-west-1", "bedrock"), "换 region 必须换签名");
        assertNotEquals(baseline, sign("POST", canonicalUri, "bedrock-runtime.us-east-1.amazonaws.com",
                CONVERSE_BODY, "us-east-1", "bedrock-mantle"), "换 service 必须换签名");
        assertNotEquals(baseline, sign("POST", canonicalUri, "bedrock-runtime.us-east-1.amazonaws.com",
                CONVERSE_BODY + " ", "us-east-1", "bedrock"), "换请求体必须换签名");
        assertNotEquals(baseline, sign("POST", canonicalUri, "bedrock-runtime.us-east-2.amazonaws.com",
                CONVERSE_BODY, "us-east-1", "bedrock"), "换 host 必须换签名");
    }

    @Test
    void uriEncodeShouldKeepUnreservedAndEncodeColon() {
        assertEquals("anthropic.claude-sonnet-4-20250514-v1%3A0", AwsSigV4Support.uriEncode(CONVERSE_MODEL_ID));
        assertEquals("abc-._~09AZ", AwsSigV4Support.uriEncode("abc-._~09AZ"), "unreserved 字符不应被编码");
        assertEquals("a%20b", AwsSigV4Support.uriEncode("a b"));
    }

    @Test
    void canonicalUriShouldAcceptEncodedPathAndDefaultToSlash() {
        assertEquals("/model/a%3A0/converse", AwsSigV4Support.canonicalUri("/model/a%3A0/converse"));
        assertEquals("/model/a%3A0/converse", AwsSigV4Support.canonicalUri("model/a%3A0/converse"));
        assertEquals("/", AwsSigV4Support.canonicalUri(null));
        assertEquals("/", AwsSigV4Support.canonicalUri("  "));
    }

    @Test
    void amzDateShouldAlwaysBeUtc() {
        // 固定时钟解析成 UTC 时间戳后，格式化结果必须与输入完全一致（防本地时区污染）
        assertEquals("20261021T170700Z", AwsSigV4Support.amzDate(java.time.Instant.parse("2026-10-21T17:07:00Z")));
        assertEquals("20261021", AwsSigV4Support.dateStamp(java.time.Instant.parse("2026-10-21T17:07:00Z")));
        assertTrue(AwsSigV4Support.amzDate(null).endsWith("Z"));
    }

    private String sign(String method,
                        String canonicalUri,
                        String host,
                        String body,
                        String region,
                        String service) {
        String payloadHash = AwsSigV4Support.payloadHash(body.getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", host);
        headers.put("x-amz-date", AMZ_DATE);
        String scope = AwsSigV4Support.credentialScope(DATE_STAMP, region, service);
        String canonical = AwsSigV4Support.canonicalRequest(method, canonicalUri, "", headers, payloadHash);
        String stringToSign = AwsSigV4Support.stringToSign(AMZ_DATE, scope, canonical);
        String signature = AwsSigV4Support.signature(SECRET, DATE_STAMP, region, service, stringToSign);
        return AwsSigV4Support.authorization(ACCESS, scope, AwsSigV4Support.signedHeaders(headers), signature);
    }
}
