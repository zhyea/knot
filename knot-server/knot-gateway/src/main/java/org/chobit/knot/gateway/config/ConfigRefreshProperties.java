package org.chobit.knot.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置缓存主动刷新：开关与探测节拍（{@code knot.gateway.config-refresh.*}）。
 *
 * <p>每拍是一条覆盖全部配置表的指纹查询，与配置量级无关；节拍越短，管理端改配置后生效越及时。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.gateway.config-refresh")
public class ConfigRefreshProperties {

    /** 探测节拍默认值：每 2 秒一次。 */
    public static final String DEFAULT_TICK_CRON = "*/2 * * * * *";

    /** 关闭后不探测，缓存退回 TTL 语义（最迟 15 分钟生效）。 */
    private boolean enabled = true;

    /** 探测 cron（六段，Spring 语义）。 */
    private String tickCron = DEFAULT_TICK_CRON;
}
