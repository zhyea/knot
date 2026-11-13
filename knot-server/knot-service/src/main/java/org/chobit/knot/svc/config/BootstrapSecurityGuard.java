package org.chobit.knot.svc.config;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.auth.JwtKit;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;

/**
 * 启动期安全守卫：使用开发默认密钥（JWT / 凭证加密）且未激活非生产 profile 时，直接中断启动（安全默认）。
 *
 * 非生产 profile：{@code dev} / {@code test} / {@code local} / {@code development} / {@code dev-local} /
 * {@code unit} / {@code integration} / {@code ci}。生产须以含 {@code prod} 的 profile 启动，并注入
 * {@code KNOT_JWT_SECRET} / {@code KNOT_CREDENTIAL_ENCRYPTION_KEY}；二者任一未满足即拒绝启动。
 */
@Slf4j
@Configuration
public class BootstrapSecurityGuard {

    private static final Set<String> NON_PROD_PROFILES = Set.of(
            "dev", "test", "local", "development", "dev-local", "unit", "integration", "ci");

    private final Environment environment;
    private final CredentialProperties credentialProperties;

    public BootstrapSecurityGuard(Environment environment, CredentialProperties credentialProperties) {
        this.environment = environment;
        this.credentialProperties = credentialProperties;
    }

    @PostConstruct
    public void enforce() {
        boolean devDefaultCredential = credentialProperties.isDevDefaultKey();
        boolean devDefaultJwt = JwtKit.isDevDefaultSecret();
        if (!devDefaultCredential && !devDefaultJwt) {
            return;
        }
        log.warn("[安全] 检测到开发默认密钥仍在使用（JWT={}, 凭证加密={}）。生产必须通过环境变量 "
                + "KNOT_JWT_SECRET / KNOT_CREDENTIAL_ENCRYPTION_KEY 注入强密钥。", devDefaultJwt, devDefaultCredential);
        String[] active = environment.getActiveProfiles();
        boolean nonProd = Arrays.stream(active).anyMatch(p -> NON_PROD_PROFILES.contains(p.toLowerCase()));
        if (!nonProd) {
            throw new IllegalStateException(
                    "检测到使用开发默认密钥（JWT=" + devDefaultJwt + ", 凭证加密=" + devDefaultCredential
                    + "）但未激活非生产 profile（当前 active profiles=" + Arrays.toString(active) + "）。为安全起见拒绝启动："
                    + " 本地/测试环境请加 --spring.profiles.active=local（或 dev/test）；"
                    + " 生产环境必须以 prod（或含 prod 的）profile 启动并注入 KNOT_JWT_SECRET / KNOT_CREDENTIAL_ENCRYPTION_KEY。");
        }
    }
}
