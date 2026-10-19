package org.chobit.knot.gateway.config;

import org.chobit.knot.gateway.auth.JwtKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;

/**
 * 启动期安全守卫：生产 profile 下若仍使用开发默认密钥（JWT / 凭证加密），直接中断启动。
 *
 * <p>两份密钥在环境变量缺失时回退到源码硬编码默认值，原先仅打印 WARN。
 * 但默认值随源码公开，生产一旦漏配即等于「JWT 可伪造 + 落库凭据可解密」。
 * 因此约定：生产必须以 {@code --spring.profiles.active=prod}（或含 prod 的 profile）启动，
 * 且注入 {@code KNOT_JWT_SECRET} / {@code KNOT_CREDENTIAL_ENCRYPTION_KEY}；
 * 二者任一未满足即拒绝启动。dev / test / local 等 profile 下不拦截（允许本地开发）。</p>
 */
@Configuration
public class BootstrapSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(BootstrapSecurityGuard.class);

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
        boolean prodLike = Arrays.stream(active).anyMatch(p -> p.toLowerCase().contains("prod"));
        if ((devDefaultCredential || devDefaultJwt) && !nonProd && prodLike) {
            throw new IllegalStateException(
                    "检测到生产 profile 下仍使用开发默认密钥（JWT=" + devDefaultJwt + ", 凭证加密=" + devDefaultCredential
                    + "），拒绝启动。请通过环境变量 KNOT_JWT_SECRET / KNOT_CREDENTIAL_ENCRYPTION_KEY 注入强密钥，"
                    + "并以 prod（或含 prod 的）profile 启动。");
        }
    }
}
