package org.chobit.knot.gateway.outage.admin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 摘除管理面 API 的开关与准入配置（{@code knot.outage.admin-api}）。
 * 准入两层保护：配置 {@link #token} 时请求头 {@code X-Knot-Admin-Token} 必须相符；未配置令牌时仅放行 loopback。
 * {@code enabled=false} 时控制器整体不注册（端点 404）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.outage.admin-api")
public class OutageAdminProperties {

    /**
     * 管理面 API 总开关；false 时 {@code /_admin/outages*} 整体不可达（404）。
     */
    private boolean enabled = true;

    /** 共享令牌（对应环境变量 {@code KNOT_GATEWAY_ADMIN_TOKEN}）；空表示未配置，此时仅允许 loopback 访问，多节点部署须配置。 */
    private String token = "";

    /**
     * 是否配置了非空令牌。
     */
    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
