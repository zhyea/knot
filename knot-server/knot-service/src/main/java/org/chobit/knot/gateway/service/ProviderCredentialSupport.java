package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.auth.AuthRoles;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.chobit.knot.gateway.crypto.CredentialEncryption;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.mapper.ProviderCredentialMapper;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供应商认证配置支持：在 {@code provider_credentials} 表与 API 层 {@code authConfig} 之间转换。
 */
@Component
public class ProviderCredentialSupport {

    private final ProviderCredentialMapper providerCredentialMapper;
    private final CredentialEncryption credentialEncryption;

    /**
     * Constructs a new instance.
     */
    public ProviderCredentialSupport(ProviderCredentialMapper providerCredentialMapper,
                                     CredentialEncryption credentialEncryption) {
        this.providerCredentialMapper = providerCredentialMapper;
        this.credentialEncryption = credentialEncryption;
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public Map<String, Object> toAuthConfig(ProviderCredentialEntity credential) {
        if (credential == null) {
            return defaultAuthConfig();
        }
        if (hasText(credential.getEncryptedConfig())) {
            Map<String, Object> config = JsonKit.fromJson(
                    credentialEncryption.decrypt(credential.getEncryptedConfig()),
                    new TypeReference<>() {
                    });
            if (config != null && !config.isEmpty()) {
                return new LinkedHashMap<>(config);
            }
        }
        return defaultAuthConfig();
    }

    public String credentialType(ProviderCredentialEntity credential) {
        if (credential == null) {
            return ProviderCredentialTypeEnum.API_KEY.code();
        }
        try {
            return ProviderCredentialTypeEnum.fromCode(credential.getCredentialType()).code();
        } catch (IllegalArgumentException ex) {
            return ProviderCredentialTypeEnum.CUSTOM.code();
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void saveAuthConfig(Long providerId, String credentialType, Map<String, Object> authConfig) {
        if (providerId == null) {
            return;
        }
        if (isEmptyAuthConfig(authConfig)) {
            providerCredentialMapper.deactivateByProviderId(providerId);
            return;
        }
        ProviderCredentialEntity entity = providerCredentialMapper.getActiveByProviderId(providerId);
        boolean isNew = entity == null;
        if (isNew) {
            entity = new ProviderCredentialEntity();
            entity.setProviderId(providerId);
            entity.setStatus(EntityStatusEnum.ACTIVE.code());
        }
        entity.setEncryptedConfig(encryptConfig(authConfig));
        entity.setCredentialType(ProviderCredentialTypeEnum.fromCode(credentialType).code());

        if (isNew) {
            providerCredentialMapper.insert(entity);
        } else {
            providerCredentialMapper.update(entity);
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public Map<String, Object> maskAuthConfig(Map<String, Object> authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return defaultAuthConfig();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : authConfig.entrySet()) {
            String val = stringVal(e.getValue());
            masked.put(e.getKey(), hasText(val) ? AuthRoles.MASKED_SECRET : "");
        }
        return masked;
    }

    /**
     * 合并待保存配置与已存在配置，屏蔽值占位符不会覆盖库中真实值。
     */
    public Map<String, Object> mergeAuthConfigForSave(Map<String, Object> incoming, Map<String, Object> existing) {
        if (incoming == null) {
            return null;
        }
        Map<String, Object> prev = existing != null ? existing : Map.of();
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : incoming.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String val = stringVal(e.getValue());
            if (isMaskedValue(val)) {
                if (prev.containsKey(key)) {
                    merged.put(key, prev.get(key));
                }
            } else {
                merged.put(key, e.getValue());
            }
        }
        return merged.isEmpty() && prev.isEmpty() ? null : merged;
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public static boolean isMaskedValue(String val) {
        if (!hasText(val)) {
            return false;
        }
        if (AuthRoles.MASKED_SECRET.equals(val)) {
            return true;
        }
        return val.chars().allMatch(c -> c == '*');
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static Map<String, Object> defaultAuthConfig() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(AuthConstants.API_KEY, "");
        return map;
    }

    private static boolean isEmptyAuthConfig(Map<String, Object> authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return true;
        }
        return authConfig.values().stream().allMatch(v -> !hasText(stringVal(v)));
    }

    private static String stringVal(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String encryptConfig(Map<String, Object> authConfig) {
        String json = JsonKit.toJson(authConfig);
        if (!hasText(json)) {
            return null;
        }
        return credentialEncryption.encrypt(json);
    }

}
