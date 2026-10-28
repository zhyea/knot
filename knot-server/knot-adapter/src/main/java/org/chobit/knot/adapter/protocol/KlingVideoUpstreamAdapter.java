package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.support.VideoRequestSupport;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 可灵（Kling）视频生成适配器：<b>透传 + 剥掉客户端回推地址</b>，仅此而已。
 *
 * <p>⭐ <b>为什么不翻译请求形状</b>：网关是代理，不是转译层。请求体该长什么样由客户端按上游文档自己写，
 * 写错了让上游 400 原路返回——那是客户端的错，也最容易定位。网关插手形状会带来两个后果：
 * ① 每家厂商的字段语义要在这里重新实现一遍，必然与上游文档漂移；
 * ② 校验顺手做了「兜底默认值」，错价与错参会被静默吞掉（判例：分辨率白名单之外的取值曾被
 * 折成裸串后静默回落默认价，一次错价要等月末对账才发现）。</p>
 *
 * <p>唯一必须做的一件事见 {@link VideoRequestSupport}：客户端给的 {@code callback_url} /
 * {@code webhook_url} 绝不能漏给供应商——漏了供应商就直接把终态推给客户端，那笔用量绕过
 * knot 的轮询与结算，既查不到也收不回。规范条款见 {@code 60-business-rules.md}「视频适配器只做透传」。</p>
 *
 * <p>⚠ <b>必须配异步档案才能用</b>：Kling 是「提交 + 轮询」，create 响应<b>没有任何计费字段</b>，
 * 没有 {@code async_profile_code} 会把提交回执当成终态，被「至少一秒」兜成 1 秒计费（资损）。
 *
 * <p>流式不必覆写 {@code streamSupported}：{@code VIDEO_GENERATIONS} 在
 * {@link ModelApiProtocolEnum} 里已是 {@code streamSupported=false}，重复声明只会让人以为
 * 「这个适配器关掉了流式」。</p>
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
