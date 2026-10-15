package org.chobit.knot.gateway.outage.admin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 摘除管理面 API 的属性注册：{@code knot.outage.admin-api.*} 绑定到
 * {@link OutageAdminProperties}；端点控制器由组件扫描按
 * {@code knot.outage.admin-api.enabled} 条件注册。
 */
@Configuration
@EnableConfigurationProperties(OutageAdminProperties.class)
public class OutageAdminConfiguration {
}
