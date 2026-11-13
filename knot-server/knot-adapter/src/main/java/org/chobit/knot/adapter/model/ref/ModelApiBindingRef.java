package org.chobit.knot.adapter.model.ref;

/**
 * 适配层自有的模型-协议绑定投影：只带上游调用链路真正消费的字段。
 *
 * @param apiPath               上游 API 路径，为空时用协议默认路径
 * @param requestAdapter        请求适配器编码（决定请求体如何改写）
 * @param protocol              协议编码（{@code ModelApiProtocolEnum}）
 * @param usageExtractor        整包用量提取器编码
 * @param streamUsageExtractor  流式用量提取器编码（为空时回落 usageExtractor）
 */
public record ModelApiBindingRef(String apiPath,
                                 String requestAdapter,
                                 String protocol,
                                 String usageExtractor,
                                 String streamUsageExtractor) {
}
