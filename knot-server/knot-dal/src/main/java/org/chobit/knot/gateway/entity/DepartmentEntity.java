package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DepartmentEntity {
    private Long id;
    private String deptCode;
    private String deptName;
    private Long parentId;
    private Integer status;
    private Integer sortOrder;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
