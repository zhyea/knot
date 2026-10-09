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
                          Long deptId) {
    }

    /** 归属用户（按 username 绑定，非主键 id；realName 由 ks_users join 派生展示） */
    public record UserInfo(String username,
                           String realName) {
    }

    public record DepartmentInfo(Long id,
                                 String name) {
    }
}
