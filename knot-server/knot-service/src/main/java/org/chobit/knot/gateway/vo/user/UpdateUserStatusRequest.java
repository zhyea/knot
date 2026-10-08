package org.chobit.knot.gateway.vo.user;

/** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
public record UpdateUserStatusRequest(Integer status) {
}
