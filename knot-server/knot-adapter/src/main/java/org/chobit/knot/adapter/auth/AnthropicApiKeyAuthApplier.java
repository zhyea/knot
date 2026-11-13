package org.chobit.knot.adapter.auth;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Anthropic 鉴权：写 {@code x-api-key: <apiKey>} 与协议固定头 {@code anthropic-version}。
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
    public void apply(HttpHeaders headers, UpstreamRequestContext context) {
        String apiKey = AuthConfigSupport.apiKey(context.authConfig());
        if (StringUtils.isNotBlank(apiKey)) {
            headers.add(GatewayHeaders.X_API_KEY, apiKey);
        }
        headers.add(GatewayHeaders.ANTHROPIC_VERSION, ANTHROPIC_VERSION);
    }
}
