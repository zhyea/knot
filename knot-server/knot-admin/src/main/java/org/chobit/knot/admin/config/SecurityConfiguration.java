package org.chobit.knot.admin.config;

import org.chobit.knot.admin.security.BruteForceGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 安全相关 Bean：防爆破计数器。两套独立配置，便于按场景调参。
 */
@Configuration
public class SecurityConfiguration {

    /** 登录防爆破：按用户名计数，默认 5 分钟窗口内 10 次失败即封禁。 */
    @Bean
    public BruteForceGuard loginBruteForceGuard(
            @Value("${knot.security.login.max-attempts:10}") long maxAttempts,
            @Value("${knot.security.login.window-seconds:300}") long windowSeconds) {
        return new BruteForceGuard(maxAttempts, Duration.ofSeconds(windowSeconds));
    }

    /** 消费者鉴权防爆破：按客户端 IP 计数，默认 1 分钟窗口内 100 次失败即封禁（阈值宽松，避免误伤 NAT 后合法客户端）。 */
    @Bean
    @Qualifier("consumerAuthBruteForceGuard")
    public BruteForceGuard consumerAuthBruteForceGuard(
            @Value("${knot.security.consumer-auth.max-attempts:100}") long maxAttempts,
            @Value("${knot.security.consumer-auth.window-seconds:60}") long windowSeconds) {
        return new BruteForceGuard(maxAttempts, Duration.ofSeconds(windowSeconds));
    }
}
