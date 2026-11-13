package org.chobit.knot.adapter.protocol;

import java.util.List;

/**
 * 流式响应适配器：SSE 单行进、0..N 行出，粒度为整行（含非 {@code data} 行，可抑制或合成 {@code event:} 行）。
 * 每请求一个实例（由 {@link UpstreamAdapter#streamAdapter} 新建），跨事件状态挂在实例上，不挂在单例 Bean 上。
 * 返回列表语义：空列表 = 抑制该行；单元素 = 改写；多元素 = 合成。默认实现 {@link #PASSTHROUGH} 原样返回。
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
