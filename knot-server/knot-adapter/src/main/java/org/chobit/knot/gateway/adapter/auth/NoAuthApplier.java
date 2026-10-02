package org.chobit.knot.gateway.adapter.auth;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;

/**
 * 不注入任何鉴权头（纯透传 / 上游无需凭据）。
 */
@Component
@Order(30)
public class NoAuthApplier implements UpstreamAuthApplier {

    public static final String CODE = "NONE";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "不注入鉴权头（透传）";
    }

    @Override
    public Set<ProviderCredentialTypeEnum> credentialTypes() {
        // 不读取任何凭据字段，对所有认证类型都可用
        return Set.of(ProviderCredentialTypeEnum.values());
    }

    @Override
    public void apply(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context) {
        // 不注入任何鉴权头
    }
}
