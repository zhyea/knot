package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class GatewayNodeEntity {
    private Long id;
    private String nodeId;
    private String host;
    private String status;
}
