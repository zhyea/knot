package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knot.gateway")
public class GatewayRuntimeProperties {

    /**
     * 网关运行时 Base URL，用于管理端路由规则测试转发
     */
    private String baseUrl = "http://127.0.0.1:9090";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
