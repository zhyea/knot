package org.chobit.knot.gateway.dto.routing;

public record TestRequestPresetDto(Long id,
                                   String code,
                                   String name,
                                   String protocolCode,
                                   String requestBody,
                                   String remark,
                                   String status) {
}
