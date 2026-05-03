package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AlertEntity {
    private Long id;
    private String level;
    private String title;
    private String status;
}
