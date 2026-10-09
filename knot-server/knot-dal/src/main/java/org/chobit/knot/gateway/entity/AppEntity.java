package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AppEntity {
    private Long id;
    /** 应用业务码（kb_apps.app_code），非主键 id */
    private String appCode;
    private String name;
    private Long deptId;
    /**
     * 关联 ks_departments.dept_name，仅查询展示
     */
    private String deptName;
    /** 归属用户登录名（业务码，绑定 username，非主键 id） */
    private String ownerUsername;
    /**
     * 关联 ks_users.real_name，仅查询展示
     */
    private String ownerRealName;
    private String remark;
    private Integer isDeleted;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
