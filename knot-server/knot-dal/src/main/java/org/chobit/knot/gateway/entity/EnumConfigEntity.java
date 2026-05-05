package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class EnumConfigEntity {
    private Long id;
    private String category;
    private String itemCode;
    private String itemLabel;
    private Integer sortOrder;
    private Boolean isSystem;
    private Boolean isEnabled;
    private String remark;
}
