package org.chobit.knot.gateway.plugin.moderation;

import org.chobit.knot.gateway.plugin.components.ModerationWordCache;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感词审核装配：全局唯一一份策略，无资源级绑定解析。
 *
 * <p>只装配审核运行时：事件 sink + 守卫。词库编译缓存（{@link ModerationWordCache}）与
 * 本配置类均为 {@code @Component} / {@code @EnableConfigurationProperties} 自注册，
 * 无需在此显式声明。词库数据由网关每请求经 {@code GatewayPluginContext.words} 下发
 * （网关只提供资源），守卫经 IOC 取缓存编译快照——本模块不碰持久层。</p>
 */
@Configuration
@EnableConfigurationProperties(PluginProperties.class)
public class ModerationConfiguration {

    /**
     * Declares a bean. Executes the public operation.
     */
    @Bean
    public ModerationEventSink moderationEventSink() {
        return new ModerationEventSink();
    }

    /**
     * 敏感词审核守卫：注入事件 sink、词库编译缓存与插件配置（均经 Spring IOC）。
     */
    @Bean
    public ModerationGuard moderationGuard(ModerationEventSink eventSink,
                                           ModerationWordCache wordCache,
                                           PluginProperties properties) {
        return new ModerationGuard(eventSink, wordCache, properties);
    }
}
