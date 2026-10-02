package org.chobit.knot.gateway.adapter.request;

import org.chobit.knot.gateway.adapter.AuthScheme;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 透传适配器（默认）。
 *
 * <p>不做任何协议转换、鉴权头注入或字段改写，直接把原始请求体按默认路径转发给上游。
 * 适用于上游已自行携带鉴权信息、且请求格式与网关协议默认约定一致的场景。</p>
 */
@Component
@Order(0)
public class PassthroughRequestAdapter implements UpstreamRequestAdapter {

    public static final String CODE = "PASSTHROUGH";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.values());

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "透传（默认）";
    }

    @Override
    public Set<ModelApiProtocolEnum> protocol() {
        return PROTOCOLS;
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        return context.requestBody();
    }

    @Override
    public AuthScheme authScheme() {
        return AuthScheme.NONE;
    }
}
