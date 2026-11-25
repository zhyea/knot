package org.chobit.knot.svc.vo.routing;

import java.util.List;

/**
 * 路由规则调试面板一次性取齐的上下文：逐目标协议、全部目标并集，以及两个作用域各自筛好的预设请求。
 *
 * <p>三层数据同源、同一趟算完，前端不再分页也不再自行筛选：</p>
 * <ul>
 *   <li>{@code targets}：逐目标（模型 / 模型池）的模型族、可调试协议与该目标作用域内的预设请求；</li>
 *   <li>{@code protocols}：全部目标协议的并集 —— 对应「全部目标」调试；</li>
 *   <li>{@code presets}：全部目标作用域内的预设请求并集（模型族优先、否则按协议筛）。</li>
 * </ul>
 *
 * <p>数据源与调试准入校验同源（{@code supportedProtocolsForTarget}），前端不解析模型 / 模型池绑定。</p>
 */
public record RoutingRuleDebugContext(List<RoutingDebugTargetItem> targets,
                                      List<String> protocols,
                                      List<RoutingDebugPresetItem> presets) {
}
