package org.chobit.knot.gateway.adapter.auth;

import org.chobit.knot.gateway.adapter.upstream.UpstreamAccess;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.adapter.upstream.UpstreamSignableRequest;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.adapter.model.ref.ProviderAccountRef;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AWS SigV4 鉴权器回归测试：走完整的「URL 解析 → host 提取 → 规范 URI → 签名」链路，
 * 与 botocore 基准（{@code audit/aws-sigv4-reference-2026-10-22.json}）逐字符对拍。
 */
class AwsSigV4AuthApplierTest {

    private static final String ACCESS = "AKIAEXAMPLEAWSBEDROCK";
    private static final String SECRET = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
    private static final Instant NOW = Instant.parse("2026-10-21T17:07:00Z");
    private static final String AMZ_DATE = "20261021T170700Z";

    private final AwsSigV4AuthApplier applier = new AwsSigV4AuthApplier();

    @Test
    void shouldSignRuntimeConverseIdenticalToAwsSdk() {
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"hi\"}]}],\"inferenceConfig\":{\"maxTokens\":1024}}";
        String url = "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-sonnet-4-20250514-v1%3A0/converse";

        HttpHeaders headers = sign(url, body, Map.of(
                "accessKey", ACCESS,
                "secretKey", SECRET,
                "region", "us-east-1",
                "endpointType", "bedrock-runtime"));

        assertEquals("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLEAWSBEDROCK/20261021/us-east-1/bedrock/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=d38947ea1ef4b10033ef1adfe6b3c7d1e2b4468858cc585cef71a2075d706152",
                headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(AMZ_DATE, headers.getFirst("X-Amz-Date"));
        assertEquals("bedrock-runtime.us-east-1.amazonaws.com", headers.getFirst(HttpHeaders.HOST));
    }

    @Test
    void shouldSignMantleWithBedrockMantleService() {
        String body = "{\"model\":\"openai.gpt-5.6-luna\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
        String url = "https://bedrock-mantle.us-east-1.api.aws/v1/chat/completions";

        HttpHeaders headers = sign(url, body, Map.of(
                "accessKey", ACCESS,
                "secretKey", SECRET,
                "region", "us-east-1",
                "endpointType", "bedrock-mantle"));

        assertTrue(headers.getFirst(HttpHeaders.AUTHORIZATION).contains("/bedrock-mantle/aws4_request"),
                "mantle 的 SigV4 service 必须是 bedrock-mantle");
        assertEquals("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLEAWSBEDROCK/20261021/us-east-1/bedrock-mantle/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=baa28e4e21277faa4420f02442d07bd8f6b1dcd90f1331bc5bf3b9c2f88c46e4",
                headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("bedrock-mantle.us-east-1.api.aws", headers.getFirst(HttpHeaders.HOST));
    }

    @Test
    void shouldSkipSigningWhenCredentialsAreIncomplete() {
        HttpHeaders headers = new HttpHeaders();
        UpstreamRequestContext context = context(Map.of("region", "us-east-1", "endpointType", "bedrock-runtime"));
        applier.sign(new UpstreamSignableRequest("POST", "https://bedrock-runtime.us-east-1.amazonaws.com/model/a/converse",
                "ignored"), headers, context, NOW);

        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION), "缺 AK/SK 时不签名，交给上游以明确 403 回敬");
    }

    @Test
    void shouldDeclareAwsAuthCredentialTypeAndFinalizedPayload() {
        assertEquals("AWS_SIGV4", applier.code());
        assertTrue(applier.credentialTypes().contains(
                org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum.AWS_AUTH));
        assertTrue(applier.requiresFinalizedPayload(), "SigV4 必须先把请求体定稿成字节才能签名");
        assertFalse(applier.requiresBaseUrl(), "AWS 端点可由 region + endpointType 派生，账户 base URL 允许留空");
    }

    // ==================== baseUrl()：AWS 特例的唯一归属地 ====================

    @Test
    void baseUrlShouldPreferProviderAccountConfig() {
        ProviderAccountRef provider = new ProviderAccountRef(1L, "aws-bedrock-runtime",
                "https://vpce-0abc.bedrock-runtime.us-east-1.vpce.amazonaws.com", "AWS");
        UpstreamRequestContext context = context(Map.of(
                "region", "us-east-1", "endpointType", "bedrock-runtime"), provider);

        assertEquals("https://vpce-0abc.bedrock-runtime.us-east-1.vpce.amazonaws.com", applier.baseUrl(context),
                "VPC / 私有代理场景：账户显式配置优先，派生逻辑让位");
    }

    @Test
    void baseUrlShouldDeriveWhenProviderAccountIsBlank() {
        ProviderAccountRef provider = new ProviderAccountRef(1L, "aws-bedrock-mantle", null, "AWS");
        UpstreamRequestContext context = context(Map.of(
                "region", "ap-northeast-1", "endpointType", "bedrock-mantle"), provider);

        assertEquals("https://bedrock-mantle.ap-northeast-1.api.aws", applier.baseUrl(context));
    }

    @Test
    void baseUrlShouldBeNullWhenRegionMissingEvenIfAccountConfigured() {
        ProviderAccountRef provider = new ProviderAccountRef(1L, "aws-bedrock-runtime",
                "https://bedrock-runtime.us-east-1.amazonaws.com", "AWS");
        UpstreamRequestContext context = context(Map.of("endpointType", "bedrock-runtime"), provider);

        assertNull(applier.baseUrl(context),
                "缺 region 无论如何都签不出来，按配置缺失上报比让上游回 403 SignatureDoesNotMatch 更可诊断");
    }

    @Test
    void prepareShouldCarryBaseUrlButNoStaticHeaders() {
        ProviderAccountRef provider = new ProviderAccountRef(1L, "aws-bedrock-runtime", null, "AWS");
        UpstreamRequestContext context = context(Map.of("region", "us-east-1"), provider);

        UpstreamAccess access = applier.prepare(context);
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com", access.baseUrl());
        assertTrue(access.headers().isEmpty(), "SigV4 没有静态头，签名头在请求体定稿后追加");
    }

    private HttpHeaders sign(String url, String body, Map<String, Object> authConfig) {
        HttpHeaders headers = new HttpHeaders();
        UpstreamRequestContext context = context(authConfig);
        applier.sign(new UpstreamSignableRequest("POST", url,
                org.chobit.knot.gateway.adapter.support.aws.AwsSigV4Support.payloadHash(
                        body.getBytes(StandardCharsets.UTF_8))), headers, context, NOW);
        return headers;
    }

    private UpstreamRequestContext context(Map<String, Object> authConfig) {
        return context(authConfig, null);
    }

    private UpstreamRequestContext context(Map<String, Object> authConfig, ProviderAccountRef provider) {
        return new UpstreamRequestContext(ModelApiProtocolEnum.CHAT_COMPLETIONS, Map.of(), null,
                null, provider, authConfig, null, null, null);
    }
}
