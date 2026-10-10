package org.chobit.knot.gateway.model;

public record GatewayRoutingInfo(RuleInfo rule,
                                 ConsumerInfo consumer,
                                 AppInfo app,
                                 UserInfo consumerUser,
                                 UserInfo appOwner,
                                 DepartmentInfo department) {

    public record RuleInfo(Long id,
                           String code,
                           String name,
                           String appScenario,
                           /** 归属用户登录名（按 username 绑定，非主键 id） */
                           String username) {
    }

    public record ConsumerInfo(Long id,
                               String code,
                               String name,
                               String secretKey,
                               boolean returnUsageDetail) {
    }

    public record AppInfo(Long id,
                          String appId,
                          String name,
                          /** 归属部门业务码（ks_departments.dept_code，非主键 id） */
                          String deptCode) {
    }

    /** 归属用户（按 username 绑定，非主键 id；realName 由 ks_users join 派生展示） */
    public record UserInfo(String username,
                           String realName) {
    }

    /** 归属部门（按 dept_code 业务码绑定，非主键 id） */
    public record DepartmentInfo(String deptCode,
                                 String name) {
    }
}
