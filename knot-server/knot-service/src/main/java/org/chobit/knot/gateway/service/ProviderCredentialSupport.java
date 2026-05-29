package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.auth.AuthRoles;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.EntityStatus;
import org.chobit.knot.gateway.crypto.CredentialEncryption;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.mapper.ProviderCredentialMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 供应商认证配置：{@code provider_credentials} 与 API 层 KV {@code authConfig} 互转。
 */
@Component
public class ProviderCredentialSupport {

    private static final String TYPE_API_KEY = "API_KEY";
    private static final String TYPE_TOKEN = "TOKEN";

    private final ProviderCredentialMapper providerCredentialMapper;
    private final CredentialEncryption credentialEncryption;

    public ProviderCredentialSupport(ProviderCredentialMapper providerCredentialMapper,
                                      CredentialEncryption credentialEncryption) {
        this.providerCredentialMapper = providerCredentialMapper;
        this.credentialEncryption = credentialEncryption;
    }

    public Map<String, Object> toAuthConfig(ProviderCredentialEntity credential) {
        if (credential == null) {
            return defaultAuthConfig();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (hasText(credential.getEncryptedKey())) {
            map.put(AuthConstants.API_KEY, decryptField(credential.getEncryptedKey()));
        }
        if (hasText(credential.getEncryptedSecret())) {
            map.put("apiSecret", decryptField(credential.getEncryptedSecret()));
        }
        if (hasText(credential.getTokenValue())) {
            map.put("token", decryptField(credential.getTokenValue()));
        }
        return map.isEmpty() ? defaultAuthConfig() : map;
    }

    public Map<Long, Map<String, Object>> loadAuthConfigBatch(List<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Map.of();
        }
        return providerCredentialMapper.listActiveByProviderIds(providerIds).stream()
                .collect(Collectors.toMap(
                        ProviderCredentialEntity::getProviderId,
                        this::toAuthConfig,
                        (a, b) -> a
                ));
    }

    public void saveAuthConfig(Long providerId, Map<String, Object> authConfig) {
        if (providerId == null) {
            return;
        }
        if (isEmptyAuthConfig(authConfig)) {
            providerCredentialMapper.deactivateByProviderId(providerId);
            return;
        }
        String apiKey = stringVal(authConfig.get(AuthConstants.API_KEY));
        String apiSecret = firstNonBlank(
                stringVal(authConfig.get("apiSecret")),
                stringVal(authConfig.get("secretKey"))
        );
        String token = firstNonBlank(
                stringVal(authConfig.get("token")),
                stringVal(authConfig.get("accessToken"))
        );

        ProviderCredentialEntity entity = providerCredentialMapper.getActiveByProviderId(providerId);
        boolean isNew = entity == null;
        if (isNew) {
            entity = new ProviderCredentialEntity();
            entity.setProviderId(providerId);
            entity.setStatus(EntityStatus.ACTIVE);
        }
        entity.setEncryptedKey(encryptField(apiKey));
        entity.setEncryptedSecret(encryptField(apiSecret));
        entity.setTokenValue(encryptField(token));
        entity.setCredentialType(hasText(token) && !hasText(apiKey) ? TYPE_TOKEN : TYPE_API_KEY);

        if (isNew) {
            providerCredentialMapper.insert(entity);
        } else {
            providerCredentialMapper.update(entity);
        }
    }

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
     * 非管理员保存时：掩码占位不覆盖库中已有明文。
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

    public static boolean isMaskedValue(String val) {
        if (!hasText(val)) {
            return false;
        }
        if (AuthRoles.MASKED_SECRET.equals(val)) {
            return true;
        }
        return val.chars().allMatch(c -> c == '*');
    }

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

    private String encryptField(String plain) {
        if (!hasText(plain)) {
            return null;
        }
        return credentialEncryption.encrypt(plain);
    }

    private String decryptField(String stored) {
        if (!hasText(stored)) {
            return null;
        }
        return credentialEncryption.decrypt(stored);
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
