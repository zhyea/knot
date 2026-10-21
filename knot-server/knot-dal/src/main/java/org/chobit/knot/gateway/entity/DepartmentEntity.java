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
    /** 只读派生：按 dept_code 绑定该部门的用户 + 应用数；0 表示可改机构编码 */
    private Long boundCount;
}
