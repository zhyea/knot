package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 {@link UsageExtractionProperties} 绑定（同 {@code JevJudgeConfiguration} 的模式）。
 *
 * <p>单独成一个空配置类而不把 {@code @EnableConfigurationProperties} 挂到
 * {@code UpstreamWebClientConfiguration} 上：后者装配的是上游 HTTP 客户端（连接池/超时），
 * 与计费语义无关，混在一起会让「改提取失败口径」看起来像在动网络层。</p>
 */
@Configuration
@EnableConfigurationProperties(UsageExtractionProperties.class)
public class UsageExtractionConfiguration {
}
