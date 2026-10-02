package org.chobit.knot.gateway.adapter;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 上游请求鉴权头集中注入器（独立组件）。
 *
 * <p>各请求适配器只负责请求/响应转换（body / path / content-type / usage 解析），
 * 不再各自实现 {@code applyHeaders}；鉴权头的注入统一收口到本组件，
 * 由适配器通过 {@link UpstreamRequestAdapter#authScheme()} 声明其所需的鉴权方案。</p>
 */
@Component
public class UpstreamAuthApplier {

    private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

    /**
     * 按适配器声明的鉴权方案，把对应鉴权头写入请求。无方案或缺少密钥时静默跳过。
     */
    public void apply(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context, AuthScheme scheme) {
        if (scheme == null || AuthScheme.NONE == scheme) {
            return;
        }
        String apiKey = AuthConfigSupport.apiKey(context.authConfig());
        if (AuthScheme.BEARER == scheme) {
            if (StringUtils.isNotBlank(apiKey)) {
                requestSpec.header(GatewayHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + apiKey);
            }
            return;
        }
        if (AuthScheme.API_KEY == scheme) {
            if (StringUtils.isNotBlank(apiKey)) {
                requestSpec.header(GatewayHeaders.X_API_KEY, apiKey);
            }
            // anthropic-version 是 Anthropic API 的固定协议头，与 x-api-key 一同注入
            requestSpec.header(GatewayHeaders.ANTHROPIC_VERSION, DEFAULT_ANTHROPIC_VERSION);
        }
    }
}
