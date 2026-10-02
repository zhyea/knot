package org.chobit.knot.gateway.adapter.auth;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.AuthConfigSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
    public void apply(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context) {
        String apiKey = AuthConfigSupport.apiKey(context.authConfig());
        if (StringUtils.isNotBlank(apiKey)) {
            requestSpec.header(GatewayHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + apiKey);
        }
    }
}
