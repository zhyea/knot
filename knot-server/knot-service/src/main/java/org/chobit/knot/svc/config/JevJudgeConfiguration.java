package org.chobit.knot.svc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 {@link JevJudgeProperties} 绑定（类 {@code GatewayRuntimeProperties} 模式）。
 */
@Configuration
@EnableConfigurationProperties(JevJudgeProperties.class)
public class JevJudgeConfiguration {
}
