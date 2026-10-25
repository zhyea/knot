package org.chobit.knot.gateway.adapter.support.aws;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.support.PayloadMapSupport;

import java.util.Locale;
import java.util.Map;

/**
 * AWS Bedrock 端点解析器：由凭据里的 {@code endpointType} 与 {@code region} 推导
 * 上游 host 与 SigV4 签名 service 名。纯函数、无 Spring 依赖。
 *
 * <p><b>两类端点的形态完全不同，不可按同一个模板拼</b>（2026-10-21 按 AWS 官方文档核实）：</p>
 * <table>
 *   <caption>Bedrock 端点形态</caption>
 *   <tr><th>endpointType</th><th>host</th><th>SigV4 service</th><th>API</th></tr>
 *   <tr><td>bedrock-runtime</td><td>{@code bedrock-runtime.<region>.amazonaws.com}</td>
 *       <td>{@code bedrock}</td><td>Converse / InvokeModel 原生 + OpenAI 兼容</td></tr>
 *   <tr><td>bedrock-mantle</td><td>{@code bedrock-mantle.<region>.api.aws}</td>
 *       <td>{@code bedrock-mantle}</td><td>仅 OpenAI 兼容 + Anthropic Messages</td></tr>
 * </table>
 *
 * <p>注意 mantle 比 runtime 多一段 {@code api.}，且签名 service 名是 {@code bedrock-mantle}
 * 而不是 {@code bedrock}——写错 service 会让所有请求 403。</p>
 */
public final class AwsBedrockEndpointResolver {

    public static final String RUNTIME = "bedrock-runtime";
    public static final String MANTLE = "bedrock-mantle";
    public static final String DEFAULT_ENDPOINT_TYPE = RUNTIME;

    /** 凭据字段键：与 {@code ProviderCredentialFields} 里 AWS_AUTH 的字段名保持一致。 */
    public static final String REGION_KEY = "region";
    public static final String ENDPOINT_TYPE_KEY = "endpointType";

    private static final String SERVICE_RUNTIME = "bedrock";
    private static final String SERVICE_MANTLE = "bedrock-mantle";

    private AwsBedrockEndpointResolver() {
    }

    /**
     * SigV4 签名的 service 名：{@code bedrock-runtime→bedrock}、{@code bedrock-mantle→bedrock-mantle}。
     *
     * <p>未知端点类型按前缀推断（{@code bedrock-x} → {@code bedrock}），保证映射表漏配时
     * 也不会退化成空串把签名打乱。</p>
     */
    public static String serviceName(String endpointType) {
        String type = StringUtils.trimToEmpty(endpointType).toLowerCase(Locale.ROOT);
        if (RUNTIME.equals(type)) {
            return SERVICE_RUNTIME;
        }
        if (MANTLE.equals(type)) {
            return SERVICE_MANTLE;
        }
        return StringUtils.substringBefore(type, "-");
    }

    /** runtime 端点的分区域名。 */
    public static String runtimeDomain(String region) {
        if (isEuropeanSovereign(region)) {
            return "amazonaws.eu";
        }
        if (isChina(region)) {
            return "amazonaws.com.cn";
        }
        if (isGovCloud(region)) {
            return "amazonaws-us-gov.com";
        }
        return "amazonaws.com";
    }

    /**
     * mantle 端点的分区域名。
     *
     * <p>GovCloud 的 mantle 与商用区同用 {@code api.aws}（AWS 文档给出的
     * {@code bedrock-mantle.us-gov-west-1.api.aws} 即为该形态），这点与 runtime 不同。</p>
     */
    public static String mantleDomain(String region) {
        if (isEuropeanSovereign(region)) {
            return "api.amazonwebservices.eu";
        }
        if (isChina(region)) {
            return "api.amazonwebservices.com.cn";
        }
        return "api.aws";
    }

    /**
     * 推导上游 host（不含 scheme 与路径）。
     *
     * @throws IllegalArgumentException endpointType 不是已知类型时抛出——宁可显式报错，
     *                                  也不要拼出一个「看起来对」的 host 让调用方收到莫名的 403/404
     */
    public static String host(String endpointType, String region) {
        String type = StringUtils.trimToEmpty(endpointType).toLowerCase(Locale.ROOT);
        String normalizedRegion = StringUtils.trimToEmpty(region).toLowerCase(Locale.ROOT);
        if (RUNTIME.equals(type)) {
            return "bedrock-runtime." + normalizedRegion + "." + runtimeDomain(normalizedRegion);
        }
        if (MANTLE.equals(type)) {
            return "bedrock-mantle." + normalizedRegion + "." + mantleDomain(normalizedRegion);
        }
        throw new IllegalArgumentException("Unsupported bedrock endpointType: " + endpointType);
    }

    /**
     * 从解密后的凭据配置推导 base URL（含 {@code https://} 前缀——WebClient 的 {@code uri()}
     * 只接受绝对 URI）。
     *
     * <p>仅供 AWS 接入策略（{@code AwsSigV4AuthApplier.baseUrl()}）调用：那里先看账户
     * {@code kb_provider_accounts.base_url} 是否显式配置，为空才落到本方法。通用策略与
     * 请求适配器都不该感知 AWS 端点推导。</p>
     *
     * @return 缺 {@code region} 时返回 {@code null}（{@code endpointType} 有默认值
     * {@code bedrock-runtime}，不会因此返回 null），由调用方按「配置缺失」上报
     */
    public static String baseUrlFrom(Map<String, Object> authConfig) {
        String region = region(authConfig);
        String endpointType = endpointType(authConfig);
        if (StringUtils.isBlank(region) || StringUtils.isBlank(endpointType)) {
            return null;
        }
        return "https://" + host(endpointType, region);
    }

    public static String region(Map<String, Object> authConfig) {
        return PayloadMapSupport.text(authConfig, REGION_KEY);
    }

    /** 凭据里的端点类型；缺省按 {@link #DEFAULT_ENDPOINT_TYPE}（bedrock-runtime）处理。 */
    public static String endpointType(Map<String, Object> authConfig) {
        String value = PayloadMapSupport.text(authConfig, ENDPOINT_TYPE_KEY);
        return StringUtils.isBlank(value) ? DEFAULT_ENDPOINT_TYPE : value;
    }

    public static String serviceName(Map<String, Object> authConfig) {
        return serviceName(endpointType(authConfig));
    }

    private static boolean isEuropeanSovereign(String region) {
        return region.startsWith("eusc-");
    }

    private static boolean isChina(String region) {
        return region.startsWith("cn-");
    }

    private static boolean isGovCloud(String region) {
        return region.startsWith("us-gov-");
    }
}
