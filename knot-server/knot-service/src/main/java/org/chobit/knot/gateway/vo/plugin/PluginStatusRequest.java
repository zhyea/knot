package org.chobit.knot.gateway.vo.plugin;

/** 状态（PluginInstanceStatusEnum）：1-草稿 2-生效 3-暂停 4-归档 */
public record PluginStatusRequest(Integer status) {
}
