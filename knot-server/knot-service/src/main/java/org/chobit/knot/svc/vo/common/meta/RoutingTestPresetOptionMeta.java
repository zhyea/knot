package org.chobit.knot.svc.vo.common.meta;

/**
 * 测试请求预设下拉候选项的 meta。
 * {@code value} 是预设主键 {@code id}；接口协议 {@code protocolCode} 决定调试面板选用的协议适配器，
 * 是绑定键之外的业务属性，落在 meta 而非顶层。
 */
public record RoutingTestPresetOptionMeta(String protocolCode) {
}
