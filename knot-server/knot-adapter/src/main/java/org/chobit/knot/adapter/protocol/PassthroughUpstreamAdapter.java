package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.support.VideoRequestSupport;
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
 *
 * <p>⭐ 唯一的例外是视频协议：即便绑定没指定视频适配器、落到本透传兜底，
 * {@code callback_url} / {@code webhook_url} 也必须剥掉（见 {@link VideoRequestSupport}）。
 * 漏了它，供应商会把终态直接推给客户端，那笔用量绕过 knot 的轮询与结算——
 * 越是「没配适配器」的绑定越容易漏，所以兜底路径也要守同一条规则。</p>
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
        // 非视频协议直接返回原实例（零拷贝）：为一次不可能命中的字段扫描复制整包请求体不值得
        if (context != null && context.protocol() == ModelApiProtocolEnum.VIDEO_GENERATIONS) {
            return VideoRequestSupport.passthroughBody(context);
        }
        return context == null ? null : context.requestBody();
    }
}
