package org.chobit.knot.gateway.async.admin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步任务管理面 API 的开关与准入（{@code knot.gateway.async-task.admin-api.*}）。
 *
 * <p>⭐ <b>为什么不复用 {@code knot.gateway.async-task.enabled}</b>：那个开关管的是
 * 「本进程要不要扫描与轮询任务」——它是<b>部署</b>口径，多节点里通常只有一两个节点打开。
 * 而管理面 API 是「本进程接不接受运维指令」，与扫描无关：关掉轮询的节点照样应该能被管理端
 * 调用（它照样连着同一个库）。两个开关合成一个，会出现「想让某个节点不轮询 → 顺手把它的
 * 管理面也关了」，排查时看起来像网关挂了。</p>
 *
 * <p>⚠ <b>与摘除管理面各自独立一套</b>（{@code knot.outage.admin-api.*}）：两者的暴露面与
 * 关闭时机不同——摘除面可以在生产默认关闭，而异步任务的运维终止往往正是出事那天要用的。
 * 唯一共享的是<b>准入判定本身</b>（{@code GatewayAdminAccessSupport}）。</p>
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
