package org.chobit.knot.gateway.adapter;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.AuthConstants;

import java.util.Map;

public final class AuthConfigSupport {

    private AuthConfigSupport() {
    }

    public static String apiKey(Map<String, Object> authConfig) {
        return value(authConfig, AuthConstants.API_KEY);
    }

    public static String token(Map<String, Object> authConfig) {
        return value(authConfig, "token");
    }

    public static String value(Map<String, Object> authConfig, String key) {
        if (authConfig == null || StringUtils.isBlank(key)) {
            return null;
        }
        Object value = authConfig.get(key);
        return StringUtils.trimToNull(value == null ? null : String.valueOf(value));
    }
}
