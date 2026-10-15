package org.chobit.knot.gateway.outage.admin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 摘除管理面 API 的开关与准入配置（{@code knot.outage.admin-api}）。
 *
 * <p>网关没有全局鉴权拦截器（鉴权在请求 handler 内按 key/rule 做），故 {@code /_admin/outages}
 * 端点自带两层保护：</p>
 * <ul>
 *   <li>配置了 {@link #token} 时，请求头 {@code X-Knot-Admin-Token} 必须与之相符；</li>
 *   <li>未配置令牌时仅放行 loopback 来源（本机管理端转发场景）。</li>
 * </ul>
 *
 * <p>{@code enabled=false} 时控制器整体不注册（端点 404），用于彻底暴露面的部署。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.outage.admin-api")
public class OutageAdminProperties {

    /**
     * 管理面 API 总开关；false 时 {@code /_admin/outages*} 整体不可达（404）。
     */
    private boolean enabled = true;

    /**
     * 共享令牌（对应环境变量 {@code KNOT_GATEWAY_ADMIN_TOKEN}）；空表示未配置，
     * 此时仅允许 loopback 访问。多节点部署必须配置，否则管理端只能到本机。
     */
    private String token = "";

    /**
     * 是否配置了非空令牌。
     */
    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
