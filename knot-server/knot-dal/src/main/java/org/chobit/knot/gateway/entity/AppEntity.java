package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AppEntity {
    private Long id;
    private String appId;
    private String name;
    private Long deptId;
    private Long ownerUserId;
    /** 关联 users.real_name，仅查询展示 */
    private String ownerRealName;
    private String remark;
    private Integer isDeleted;
    private String status;
}
