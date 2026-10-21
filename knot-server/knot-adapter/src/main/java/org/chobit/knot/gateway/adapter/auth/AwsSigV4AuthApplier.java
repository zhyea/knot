package org.chobit.knot.gateway.adapter.auth;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.AuthConfigSupport;
import org.chobit.knot.gateway.adapter.aws.AwsBedrockEndpointResolver;
import org.chobit.knot.gateway.adapter.aws.AwsSigV4Support;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.adapter.upstream.UpstreamSignableRequest;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * AWS SigV4 鉴权：用 AK/SK 对「方法 + 路径 + host + 请求体哈希 + 时间戳」签名，
 * 产出 {@code Authorization} / {@code X-Amz-Date}（有临时凭据时再加 {@code X-Amz-Security-Token}）。
 *
 * <p>签名所需的 service 名由凭据里的 {@code endpointType} 决定：
 * {@code bedrock-runtime→bedrock}、{@code bedrock-mantle→bedrock-mantle}（2026-10-21 按 AWS
 * 官方文档核实，两者不是同一个 service，写错会全站 403）。</p>
 *
 * <p>只签 {@code host}、{@code x-amz-date}（及 security token）：这是 SigV4 允许的最小集，
 * 故意不签 {@code content-type} 等可能被客户端规范化的头，避免「签名算一次、发出时变了」。</p>
 */
@Component
@Order(30)
public class AwsSigV4AuthApplier implements UpstreamAuthApplier {

    public static final String CODE = "AWS_SIGV4";

    private static final String ACCESS_KEY = "accessKey";
    private static final String SECRET_KEY = "secretKey";
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String AMZ_DATE = "X-Amz-Date";
    private static final String AMZ_SECURITY_TOKEN = "X-Amz-Security-Token";
    private static final String HOST = "host";
    private static final String X_AMZ_DATE = "x-amz-date";
    private static final String X_AMZ_SECURITY_TOKEN = "x-amz-security-token";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "AWS SigV4（AK/SK 签名）";
    }

    @Override
    public Set<ProviderCredentialTypeEnum> credentialTypes() {
        return Set.of(ProviderCredentialTypeEnum.AWS_AUTH);
    }

    /**
     * SigV4 没有静态头可写：签名必须等请求体定稿后由执行器回调 {@link #sign}，
     * 因此这里刻意留空（与 {@code apply}「只写静态头」的职责保持一致）。
     */
    @Override
    public void apply(HttpHeaders headers, UpstreamRequestContext context) {
    }

    @Override
    public boolean signsRequestBody() {
        return true;
    }

    @Override
    public void sign(UpstreamSignableRequest request, HttpHeaders headers, UpstreamRequestContext context) {
        sign(request, headers, context, Instant.now());
    }

    /**
     * 带显式时钟的签名：{@code now} 固定后可与 botocore 的签名逐字符对拍
     * （AWS 时间窗只有 5 分钟，用 {@link Instant#now()} 无法稳定断言）。
     */
    public void sign(UpstreamSignableRequest request,
                     HttpHeaders headers,
                     UpstreamRequestContext context,
                     Instant now) {
        if (request == null || headers == null || context == null) {
            return;
        }
        Map<String, Object> authConfig = context.authConfig();
        String accessKey = AuthConfigSupport.value(authConfig, ACCESS_KEY);
        String secretKey = AuthConfigSupport.value(authConfig, SECRET_KEY);
        String region = AwsBedrockEndpointResolver.region(authConfig);
        if (StringUtils.isAnyBlank(accessKey, secretKey, region)) {
            // 缺凭据静默跳过：与 apply 的既有约定一致，上游会以明确的 403 回敬
            return;
        }
        URI uri = toUri(request.url());
        String service = AwsBedrockEndpointResolver.serviceName(authConfig);
        String sessionToken = AuthConfigSupport.value(authConfig, SESSION_TOKEN);

        String amzDate = AwsSigV4Support.amzDate(now);
        String dateStamp = AwsSigV4Support.dateStamp(now);
        String host = hostHeader(uri);

        Map<String, String> signedHeaders = new LinkedHashMap<>();
        signedHeaders.put(HOST, host);
        signedHeaders.put(X_AMZ_DATE, amzDate);
        if (StringUtils.isNotBlank(sessionToken)) {
            signedHeaders.put(X_AMZ_SECURITY_TOKEN, sessionToken);
        }

        String scope = AwsSigV4Support.credentialScope(dateStamp, region, service);
        String canonicalRequest = AwsSigV4Support.canonicalRequest(
                request.httpMethod(),
                AwsSigV4Support.canonicalUri(uri.getRawPath()),
                AwsSigV4Support.canonicalQuery(uri.getQuery()),
                signedHeaders,
                request.payloadHash());
        String stringToSign = AwsSigV4Support.stringToSign(amzDate, scope, canonicalRequest);
        String signature = AwsSigV4Support.signature(secretKey, dateStamp, region, service, stringToSign);

        headers.set(HttpHeaders.AUTHORIZATION,
                AwsSigV4Support.authorization(accessKey, scope,
                        AwsSigV4Support.signedHeaders(signedHeaders), signature));
        headers.set(HttpHeaders.HOST, host);
        headers.set(AMZ_DATE, amzDate);
        if (StringUtils.isNotBlank(sessionToken)) {
            headers.set(AMZ_SECURITY_TOKEN, sessionToken);
        }
    }

    /**
     * {@code Host} 头值：非默认端口要带上端口，否则签名覆盖的 host 与实际发出的不一致。
     */
    private static String hostHeader(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost();
        int port = uri.getPort();
        return port >= 0 ? host + ":" + port : host;
    }

    private static URI toUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid upstream url for SigV4 signing: " + url, e);
        }
    }
}
