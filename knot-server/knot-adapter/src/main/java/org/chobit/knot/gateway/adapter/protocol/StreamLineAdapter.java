package org.chobit.knot.gateway.adapter.protocol;

import java.util.List;

/**
 * 流式响应适配器：SSE 单行进、<b>0..N 行</b>出。
 *
 * <p>为什么是行级而不是 {@code data} 载荷级：协议级流式转换经常要动「非 data 行」——
 * Anthropic 的 {@code event: message_start} 在 OpenAI 客户端眼里是噪音必须抑制，反向
 * （chat.completion.chunk → Messages）又必须为每个 data 行合成配套的 {@code event:} 行。
 * 只给 data 载荷钩子做不到抑制与合成，这是本接口存在的唯一理由。</p>
 *
 * <p>为什么是每请求一个实例：流式转换有跨事件的必然状态——首个 chunk 只发一次
 * {@code message_start}、chunk id 必须全程一致、tool_use 块的序号要连续递增。
 * 适配器是单例 Bean，状态不能挂在适配器上，故由 {@link UpstreamAdapter#streamAdapter}
 * 按请求新建。</p>
 *
 * <p>返回列表语义：空列表 = 抑制该行；单元素 = 改写；多元素 = 合成（如 data 行 + 配套 event 行）。
 * 默认实现 {@link #PASSTHROUGH} 原样返回，逐字节不改。</p>
 */
@FunctionalInterface
public interface StreamLineAdapter {

    /** 无适配时的默认实现：原行返回，零解析、零序列化。 */
    StreamLineAdapter PASSTHROUGH = line -> List.of(line);

    /**
     * 单行适配。
     *
     * @param line 上游 SSE 原始行（含 {@code data:} / {@code event:} 前缀，不含行尾换行符；
     *             空行为 SSE 事件分隔符）
     * @return 要发给客户端的行；空列表表示该行不发
     */
    List<String> adapt(String line);
}
