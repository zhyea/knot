package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端调试链路使用的网关运行时地址。
 *
 * <p>消费方只有管理端路由规则调试（{@code RoutingRuleController} → {@code RoutingRuleService}
 * 的测试转发）：网关基地址由 controller 读取本属性后作为参数透传，knot-service 不持有该配置，
 * 调试 API 与网关部署细节解耦。</p>
 */
@ConfigurationProperties(prefix = "knot.gateway")
public class GatewayRuntimeProperties {

    /**
     * 网关运行时 Base URL，用于管理端路由规则测试转发。缺省指向本机 9090（网关生产端口）。
     */
    private String baseUrl = "http://127.0.0.1:9090";

    /**
     * Returns the requested value. Executes the public operation.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 归一化后的网关基地址：空值回落缺省端口、剔除末尾斜杠，供调试转发直接拼接路径。
     */
    public String normalizedBaseUrl() {
        String base = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:9090" : baseUrl.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
