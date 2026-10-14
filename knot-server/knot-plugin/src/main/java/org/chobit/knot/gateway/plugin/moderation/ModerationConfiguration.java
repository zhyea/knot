package org.chobit.knot.gateway.plugin.moderation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感词审核装配：全局唯一一份策略，无资源级绑定解析。
 *
 * <p>词库来源 {@link ModerationWordSource} 的实现由 knot-service 提供（依赖倒置，
 * 与 {@code PluginBindingProvider} / {@code PluginService} 同构），本模块不碰持久层。</p>
 */
@Configuration
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationConfiguration {

    /**
     * Declares a bean. Executes the public operation.
     */
    @Bean
    public ModerationEventSink moderationEventSink() {
        return new ModerationEventSink();
    }

    /**
     * 词库快照缓存：注入可配置的定时刷新间隔。定时刷新由 {@code ScheduledRefreshManager} 统一托管调度，
     * 本类只需实现 {@link org.chobit.knot.gateway.plugin.refresh.Refreshable}。
     */
    @Bean
    public ModerationSnapshotCache moderationSnapshotCache(ModerationWordSource source,
                                                          ModerationProperties properties) {
        return new ModerationSnapshotCache(
                source, ModerationConstants.VERSION_CHECK_INTERVAL_MILLIS, properties.getRefreshInterval());
    }

    /**
     * Declares a bean. Executes the public operation.
     */
    @Bean
    public ModerationGuard moderationGuard(ModerationProperties properties,
                                           ModerationSnapshotCache snapshotCache,
                                           ModerationEventSink eventSink) {
        return new ModerationGuard(properties, snapshotCache, eventSink);
    }
}
