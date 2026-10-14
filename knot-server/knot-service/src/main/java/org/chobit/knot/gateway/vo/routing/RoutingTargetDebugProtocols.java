package org.chobit.knot.gateway.vo.routing;

import java.util.List;

/**
 * 单个调试目标支持的接口协议。
 *
 * <p>protocols 为 canonical 协议编码（见 {@code ModelApiProtocolEnum#canonical}），
 * 已按目标类型解析完毕：MODEL 取模型自身的协议绑定，MODEL_POOL 取池内启用模型的协议并集。
 */
public record RoutingTargetDebugProtocols(String targetType,
                                          Long targetId,
                                          boolean primary,
                                          List<String> protocols) {
}
