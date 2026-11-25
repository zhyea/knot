package org.chobit.knot.svc.vo.routing;

/**
 * 单个预设请求的下拉项：带协议与模型族，供调试面板按当前协议展示。
 * 请求体不进本结构——选中后按 id 懒加载（{@code GET /api/test-request-presets/{id}}）。
 */
public record RoutingDebugPresetItem(Long id,
                                     String name,
                                     /** canonical 协议编码（{@code ModelApiProtocolEnum#canonical}） */
                                     String protocolCode,
                                     /** 模型族 code；null 表示通用用例，任何目标都可用 */
                                     String modelFamily) {
}
