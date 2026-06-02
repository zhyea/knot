package org.chobit.knot.gateway.model;

public record GatewayRoutingInfo(RuleInfo rule,
                                 ConsumerInfo consumer,
                                 AppInfo app,
                                 UserInfo ruleUser,
                                 UserInfo consumerUser,
                                 UserInfo appOwner,
                                 DepartmentInfo department) {

    public record RuleInfo(Long id,
                           String code,
                           String name,
                           String appScenario,
                           String modelTypes) {
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

    public record UserInfo(Long id,
                           String username,
                           String realName) {
    }

    public record DepartmentInfo(Long id,
                                 String name) {
    }
}
