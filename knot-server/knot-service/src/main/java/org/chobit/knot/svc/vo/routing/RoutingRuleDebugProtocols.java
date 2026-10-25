package org.chobit.knot.svc.vo.routing;

import java.util.List;

/**
 * 路由规则调试协议下发：逐目标给出可调试的接口协议。
 *
 * <p>数据源与调试准入校验（{@code RoutingRuleService#resolveTestProtocol}）同源，
 * 均由 {@code supportedProtocolsForTarget} 计算——前端不再自行解析模型/模型池的协议绑定，
 * 避免「下拉列出的协议」与「后端实际放行的协议」两份实现漂移。
 *
 * <p>「全部目标」模式下的协议并集由前端按各目标列表自行合并（与选择单个目标共用同一份数据）。
 */
public record RoutingRuleDebugProtocols(List<RoutingTargetDebugProtocols> targets) {
}
