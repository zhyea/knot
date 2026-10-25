package org.chobit.knot.adapter.auth;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.AuthConstants;
import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Bearer 鉴权：{@code Authorization: Bearer <apiKey>}。OpenAI / Zhipu / Qwen 等兼容接口。
 */
@Component
@Order(10)
public class BearerAuthApplier implements UpstreamAuthApplier {

    public static final String CODE = "BEARER";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Bearer Token（Authorization）";
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
            headers.add(GatewayHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + apiKey);
        }
    }
}
