package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 透传适配器（默认）：请求与响应两个方向都不做任何协议转换。
 *
 * <p>请求体原样转发，响应体原样回传；鉴权由供应商侧的鉴权策略（auth_applier）决定，
 * 与本适配器无关。适用于上游端点与网关协议默认约定一致的场景。</p>
 */
@Component
@Order(0)
public class PassthroughUpstreamAdapter implements UpstreamAdapter {

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
}
