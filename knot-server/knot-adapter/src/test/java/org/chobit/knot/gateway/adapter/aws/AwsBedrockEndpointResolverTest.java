package org.chobit.knot.gateway.adapter.aws;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Bedrock 端点解析回归测试：两类端点的 host 形态与 SigV4 service 名完全不同，
 * 拼错任意一个都会让请求 403 / 404，故逐条钉死。
 */
class AwsBedrockEndpointResolverTest {

    @Test
    void runtimeHostShouldFollowAmazonawsDomainPerPartition() {
        assertEquals("bedrock-runtime.us-east-1.amazonaws.com",
                AwsBedrockEndpointResolver.host("bedrock-runtime", "us-east-1"));
        assertEquals("bedrock-runtime.cn-north-1.amazonaws.com.cn",
                AwsBedrockEndpointResolver.host("bedrock-runtime", "cn-north-1"));
        assertEquals("bedrock-runtime.us-gov-west-1.amazonaws-us-gov.com",
                AwsBedrockEndpointResolver.host("bedrock-runtime", "us-gov-west-1"));
        assertEquals("bedrock-runtime.eusc-de-east-1.amazonaws.eu",
                AwsBedrockEndpointResolver.host("bedrock-runtime", "eusc-de-east-1"));
    }

    @Test
    void mantleHostShouldUseApiAwsDomain() {
        // mantle 比 runtime 多一段 "api."，且全球/GovCloud 都是 api.aws
        assertEquals("bedrock-mantle.us-east-1.api.aws",
                AwsBedrockEndpointResolver.host("bedrock-mantle", "us-east-1"));
        assertEquals("bedrock-mantle.us-gov-west-1.api.aws",
                AwsBedrockEndpointResolver.host("bedrock-mantle", "us-gov-west-1"));
        assertEquals("bedrock-mantle.eusc-de-east-1.api.amazonwebservices.eu",
                AwsBedrockEndpointResolver.host("bedrock-mantle", "eusc-de-east-1"));
        assertEquals("bedrock-mantle.cn-north-1.api.amazonwebservices.com.cn",
                AwsBedrockEndpointResolver.host("bedrock-mantle", "cn-north-1"));
    }

    @Test
    void serviceNameShouldDifferBetweenRuntimeAndMantle() {
        assertEquals("bedrock", AwsBedrockEndpointResolver.serviceName("bedrock-runtime"));
        assertEquals("bedrock-mantle", AwsBedrockEndpointResolver.serviceName("bedrock-mantle"));
        // 未知类型按前缀兜底，绝不退化成空串把签名打乱
        assertEquals("bedrock", AwsBedrockEndpointResolver.serviceName("bedrock-future-endpoint"));
        assertEquals("", AwsBedrockEndpointResolver.serviceName(""));
    }

    @Test
    void baseUrlFromShouldIncludeScheme() {
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com",
                AwsBedrockEndpointResolver.baseUrlFrom(Map.of("region", "us-east-1", "endpointType", "bedrock-runtime")));
        // WebClient 的 uri() 只接受绝对 URI，缺 scheme 会直接抛
        assertEquals("https://bedrock-mantle.us-east-1.api.aws",
                AwsBedrockEndpointResolver.baseUrlFrom(Map.of("region", "us-east-1", "endpointType", "bedrock-mantle")));
    }

    @Test
    void baseUrlFromShouldDefaultEndpointTypeAndFallBackToNull() {
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com",
                AwsBedrockEndpointResolver.baseUrlFrom(Map.of("region", "us-east-1")),
                "未显式给 endpointType 时按 bedrock-runtime 处理");
        assertNull(AwsBedrockEndpointResolver.baseUrlFrom(Map.of("endpointType", "bedrock-runtime")), "缺 region");
        assertNull(AwsBedrockEndpointResolver.baseUrlFrom(Map.of()), "非 AWS 凭据");
        assertNull(AwsBedrockEndpointResolver.baseUrlFrom(null));
    }

    @Test
    void unknownEndpointTypeShouldFailFast() {
        // 宁可显式报错，也不要拼出一个「看起来对」的 host 让人收到莫名的 403/404
        assertThrows(IllegalArgumentException.class,
                () -> AwsBedrockEndpointResolver.host("bedrock-unknown", "us-east-1"));
    }
}
