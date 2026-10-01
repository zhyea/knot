package org.chobit.knot.gateway.vo.routing;

public record TestRequestPreset(Long id,
                                String code,
                                String name,
                                String protocolCode,
                                String requestBody,
                                String remark,
                                String status) {
}
