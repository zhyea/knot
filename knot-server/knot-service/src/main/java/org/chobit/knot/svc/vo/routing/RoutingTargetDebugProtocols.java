package org.chobit.knot.svc.vo.routing;

import java.util.List;

/**
 * 单个调试目标支持的接口协议。
 * {@code protocols} 为 canonical 协议编码（{@code ModelApiProtocolEnum#canonical}），已按目标类型解析：
 * MODEL 取模型自身的协议绑定，MODEL_POOL 取池内启用模型的协议并集。
 * {@code modelFamily} 是该目标解析出的模型族 code（MODEL 取映射统一模型的族、MODEL_POOL 取池归属统一模型的族），
 * {@code null} 表示未归属任何统一模型，调试面板不再按族过滤预设。
 */
public record RoutingTargetDebugProtocols(String targetType,
                                          Long targetId,
                                          boolean primary,
                                          String modelFamily,
                                          List<String> protocols) {
}
