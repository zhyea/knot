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
 * <p>两份密钥在环境变量缺失时回退到源码硬编码默认值，原先仅打印 WARN。
 * 但默认值随源码公开，生产一旦漏配即等于「JWT 可伪造 + 落库凭据可解密」。
 * 因此采用安全默认：只要仍在使用开发默认密钥，就必须显式激活非生产 profile
 * （dev / test / local / development / dev-local / unit / integration / ci 之一），否则拒绝启动——
 * 这样「未设任何 profile 直接启动」的生产部署也会因命中默认密钥而被拦断，逼出显式配置。
 * 生产环境必须以 {@code --spring.profiles.active=prod}（或含 prod 的 profile）启动，
 * 且注入 {@code KNOT_JWT_SECRET} / {@code KNOT_CREDENTIAL_ENCRYPTION_KEY}；二者任一未满足即拒绝启动。</p>
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
