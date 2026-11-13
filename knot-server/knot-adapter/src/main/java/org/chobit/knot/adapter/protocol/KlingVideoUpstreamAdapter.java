package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.support.VideoRequestSupport;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 可灵（Kling）视频生成适配器：透传请求与响应，仅剥掉客户端给的 {@code callback_url} / {@code webhook_url}（见 {@link VideoRequestSupport}）。
 * 必须配异步档案（{@code async_profile_code}）才能用：create 响应不含任何计费字段，否则提交回执会被当成终态。
 * 流式无需覆写 {@code streamSupported}：{@code VIDEO_GENERATIONS} 在 {@link ModelApiProtocolEnum} 中已是 {@code streamSupported=false}。
 */
@Component
@Order(120)
public class KlingVideoUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "KLING_VIDEO";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.VIDEO_GENERATIONS);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "可灵视频生成（Kling 3.0 Turbo）";
    }

    @Override
    public Set<ModelApiProtocolEnum> protocol() {
        return PROTOCOLS;
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        return VideoRequestSupport.passthroughBody(context);
    }
}
