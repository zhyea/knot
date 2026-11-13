package org.chobit.knot.gateway.async.admin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步任务管理面 API 的开关与准入（{@code knot.gateway.async-task.admin-api.*}）。
 * 与扫描开关 {@code knot.gateway.async-task.enabled}、摘除管理面 {@code knot.outage.admin-api.*} 各自独立；
 * 唯一共享的是准入判定 {@code GatewayAdminAccessSupport}。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.gateway.async-task.admin-api")
public class AsyncTaskAdminProperties {

    /** 总开关；false 时 {@code /_admin/async-tasks*} 整体不注册（404） */
    private boolean enabled = true;

    /**
     * 共享令牌（对应环境变量 {@code KNOT_GATEWAY_ADMIN_TOKEN}，与摘除管理面同源）；
     * 空表示未配置，此时仅放行 loopback。
     */
    private String token = "";
}
