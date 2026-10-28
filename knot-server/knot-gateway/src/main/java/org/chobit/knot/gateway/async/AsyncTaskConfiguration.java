package org.chobit.knot.gateway.async;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 {@link AsyncTaskProperties} 绑定（同 {@code UsageExtractionConfiguration} 的模式）。
 *
 * <p>单独成一个空配置类而不把 {@code @EnableConfigurationProperties} 挂到
 * {@link AsyncTaskSchedulingConfiguration} 上：后者只解决「谁来发起这一轮」（调度器线程池），
 * 与「轮询节奏/批量/占位时长」这些业务旋钮无关，混在一起会让「调批量大小」看起来像在动调度层。</p>
 */
@Configuration
@EnableConfigurationProperties(AsyncTaskProperties.class)
public class AsyncTaskConfiguration {
}
