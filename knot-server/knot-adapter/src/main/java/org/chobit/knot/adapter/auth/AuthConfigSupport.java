package org.chobit.knot.adapter.auth;

import org.chobit.knot.adapter.support.PayloadMapSupport;
import org.chobit.knot.common.constants.AuthConstants;

import java.util.Map;

/**
 * authConfig（解密后的供应商凭据 Map）的域键名知识：只回答「API Key 存在哪个键」；
 * 读取语义（trim、空白视为未配置）归 {@link PayloadMapSupport#text}。
 */
public final class AuthConfigSupport {

    private AuthConfigSupport() {
    }

    public static String apiKey(Map<String, Object> authConfig) {
        return PayloadMapSupport.text(authConfig, AuthConstants.API_KEY);
    }
}
