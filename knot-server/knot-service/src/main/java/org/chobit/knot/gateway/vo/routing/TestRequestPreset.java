package org.chobit.knot.gateway.vo.routing;

public record TestRequestPreset(Long id,
                                String code,
                                String name,
                                String protocolCode,
                                String logicalModelCode,
                                String logicalModelName,
                                String requestBody,
                                String remark,
                                /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
                                Integer status) {
}
