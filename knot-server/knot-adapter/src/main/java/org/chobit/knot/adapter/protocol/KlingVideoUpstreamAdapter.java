package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.support.kling.KlingVideoSupport;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 可灵（Kling）视频生成适配器：把网关的 {@code VideoGenerationRequest} 转成 Kling 的
 * {@code {prompt|contents, settings, options}} 形态。
 *
 * <p>首个 {@code VIDEO_GENERATIONS} 适配器：此前该协议只有 {@code PassthroughUpstreamAdapter}
 * 兜底，请求体会原样打到上游——OpenAI 形状对 Kling 不生效，所以需要一个真实转换。</p>
 *
 * <p><b>本类不带逻辑</b>，转换与校验全在 {@link KlingVideoSupport}（同 {@code AwsBedrock*} 的拆法）。</p>
 *
 * <p>⚠ <b>必须配异步档案才能用</b>：Kling 是「提交 + 轮询」，create 响应<b>没有任何计费字段</b>，
 * 没有 {@code async_profile_code} 会把提交回执当成终态，被「至少一秒」兜成 1 秒计费（资损）。
 * 绑定时就位 {@code KLING_TASK}，是本适配器可被启用的前提。</p>
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
        return KlingVideoSupport.buildRequestBody(context);
    }

    /**
     * Kling 不出 SSE：异步形态下 create 只回一个 {@code task_id}，流式只是把一次 GET 伪装成长连接，
     * 没有任何增量价值。显式关掉可由执行器直接走整包路径（与 {@code VIDEO_GENERATIONS} 的
     * {@code streamSupported=false} 一致）。
     */
    @Override
    public boolean streamSupported(UpstreamRequestContext context) {
        return false;
    }
}
