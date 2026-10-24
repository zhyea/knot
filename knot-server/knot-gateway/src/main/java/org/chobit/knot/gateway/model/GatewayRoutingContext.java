package org.chobit.knot.gateway.model;

public record GatewayRoutingContext(RuleSnapshot rule,
                                 ConsumerSnapshot consumer,
                                 AppSnapshot app,
                                 UserSnapshot consumerUser,
                                 UserSnapshot appOwner,
                                 DepartmentSnapshot department) {

    public record RuleSnapshot(Long id,
                           String code,
                           String name,
                           String appScenario,
                           /** 归属用户登录名（按 username 绑定，非主键 id） */
                           String username) {
    }

    public record ConsumerSnapshot(Long id,
                               String code,
                               String name,
                               String secretKey,
                               boolean returnUsageDetail) {
    }

    public record AppSnapshot(Long id,
                          String appId,
                          String name,
                          /** 归属部门业务码（ks_departments.dept_code，非主键 id） */
                          String deptCode) {
    }

    /** 归属用户（按 username 绑定，非主键 id；realName 由 ks_users join 派生展示） */
    public record UserSnapshot(String username,
                           String realName) {
    }

    /** 归属部门（按 dept_code 业务码绑定，非主键 id） */
    public record DepartmentSnapshot(String deptCode,
                                 String name) {
    }
}
