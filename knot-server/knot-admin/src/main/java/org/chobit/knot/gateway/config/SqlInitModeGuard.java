package org.chobit.knot.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * 启动期安全守卫：生产 profile 下若 {@code spring.sql.init.mode} 仍为 {@code ALWAYS}（即未显式设
 * {@code KNOT_SQL_INIT_MODE=NEVER}），直接中断启动。
 *
 * <p>application.yml 中 {@code spring.sql.init.mode} 解析自 {@code ${KNOT_SQL_INIT_MODE:ALWAYS}}，
 * 缺省为 {@code ALWAYS}，意味着每次启动都会重新执行 schema.sql / data.sql。在开发/测试环境这是便利的
 * 自动建库行为；但在生产环境，重复初始化可能重跑数据脚本、覆盖或破坏线上表结构，极具风险。</p>
 *
 * <p>因此采用安全默认：生产 profile（dev / test / local / development / dev-local / unit / integration / ci
 * 之外）下，只要模式仍是 {@code ALWAYS}（含未设置时的默认值），即拒绝启动，逼出显式配置
 * {@code KNOT_SQL_INIT_MODE=NEVER} 并由独立的迁移脚本（docs/database/migration/）负责 schema 演进。
 * 非生产 profile 下 {@code ALWAYS} 是期望行为，放行。</p>
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
