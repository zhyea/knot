package org.chobit.knot.gateway.adapter.auth;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.AuthConfigSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;

/**
 * Anthropic 鉴权：{@code x-api-key: <apiKey>} + 固定协议头 {@code anthropic-version}。
 * anthropic-version 是 Anthropic 协议常量、不是凭据，但属于「该供应商怎么被访问」的
 * 固有知识，故随鉴权策略一起收口在这里，不放进请求适配器。
 */
@Component
@Order(20)
public class AnthropicApiKeyAuthApplier implements UpstreamAuthApplier {

    public static final String CODE = "ANTHROPIC_API_KEY";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Anthropic API Key（x-api-key）";
    }

    @Override
    public Set<ProviderCredentialTypeEnum> credentialTypes() {
        // 读取 authConfig.apiKey 作为凭据，仅适用于 api-key 认证类型
        return Set.of(ProviderCredentialTypeEnum.API_KEY);
    }

    @Override
    public void apply(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context) {
        String apiKey = AuthConfigSupport.apiKey(context.authConfig());
        if (StringUtils.isNotBlank(apiKey)) {
            requestSpec.header(GatewayHeaders.X_API_KEY, apiKey);
        }
        requestSpec.header(GatewayHeaders.ANTHROPIC_VERSION, ANTHROPIC_VERSION);
    }
}
