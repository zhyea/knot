package org.chobit.knot.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "knot.credential")
public class CredentialProperties {

    /** 开发兜底口令：只允许本地使用，启动时若命中会打印告警。 */
    public static final String DEV_DEFAULT_KEY = "knot-dev-credential-encryption-key";

    /**
     * 凭证加密口令，经 SHA-256 派生为 AES-256 密钥。
     * 生产环境必须通过环境变量 KNOT_CREDENTIAL_ENCRYPTION_KEY 注入。
     */
    private String encryptionKey = DEV_DEFAULT_KEY;

    /**
     * Returns whether the configured key is still the development default.
     */
    public boolean isDevDefaultKey() {
        return DEV_DEFAULT_KEY.equals(encryptionKey);
    }
}
