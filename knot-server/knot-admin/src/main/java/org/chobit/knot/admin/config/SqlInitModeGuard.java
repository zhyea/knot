package org.chobit.knot.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * 启动期安全守卫：生产 profile 下若 {@code spring.sql.init.mode} 仍为 {@code ALWAYS}（即未显式设
 * {@code KNOT_SQL_INIT_MODE=NEVER}），直接中断启动；该配置缺省为 {@code ALWAYS}。
 * 非生产 profile 下 {@code ALWAYS} 是期望行为，放行。
 */
@Slf4j
@Configuration
public class SqlInitModeGuard {

    private static final Set<String> NON_PROD_PROFILES = Set.of(
            "dev", "test", "local", "development", "dev-local", "unit", "integration", "ci");

    private static final String SQL_INIT_MODE_PROPERTY = "spring.sql.init.mode";

    /** 生产环境禁止的取值：一旦命中即拒启。 */
    private static final String FORBIDDEN_MODE = "always";

    private final Environment environment;

    public SqlInitModeGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void enforce() {
        String[] active = environment.getActiveProfiles();
        boolean nonProd = Arrays.stream(active)
                .anyMatch(p -> NON_PROD_PROFILES.contains(p.toLowerCase(Locale.ROOT)));
        if (nonProd) {
            return;
        }
        String mode = environment.getProperty(SQL_INIT_MODE_PROPERTY);
        if (mode == null || mode.isBlank() || FORBIDDEN_MODE.equalsIgnoreCase(mode.trim())) {
            throw new IllegalStateException(
                    "生产环境下 KNOT_SQL_INIT_MODE 仍为 ALWAYS（当前 spring.sql.init.mode=" + mode
                    + "，active profiles=" + Arrays.toString(active) + "）。每次启动重跑 schema/data 会破坏线上库，"
                    + "为安全起见拒绝启动：生产部署必须显式设置 KNOT_SQL_INIT_MODE=NEVER，"
                    + "并由 docs/database/migration/ 下的幂等迁移脚本负责表结构演进。");
        }
        log.info("[安全] 生产环境 SQL 初始化模式已显式设置为 {}，跳过 ALWAYS 启动守卫。", mode);
    }
}
