package org.chobit.knot.adapter.auth;

import org.chobit.knot.adapter.support.PayloadMapSupport;
import org.chobit.knot.common.constants.AuthConstants;

import java.util.Map;

/**
 * authConfig（解密后的供应商凭据 Map）的域键名知识：只回答「API Key 存在哪个键」。
 *
 * <p>读取力学（trim / 空白 = 未配置）归 {@link PayloadMapSupport#text}，本类不再转发一层——
 * 转发的壳没有知识增量，只是同一操作的两个名字（判例：{@code value} 曾在此委托 {@code text}，
 * 2026-10-25 删除；它的原实现还与 {@code support.aws.AwsBedrockEndpointResolver.read}
 * 逐字重复）。</p>
 */
public final class AuthConfigSupport {

    private AuthConfigSupport() {
    }

    public static String apiKey(Map<String, Object> authConfig) {
        return PayloadMapSupport.text(authConfig, AuthConstants.API_KEY);
    }
}
