package org.chobit.knot.gateway.adapter.support.aws;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * AWS Signature Version 4（SigV4）纯函数签名器。
 *
 * <p>零 Spring 依赖、零新增第三方依赖：只用 JDK 自带的 {@link MessageDigest}（SHA-256）与
 * {@link Mac}（HmacSHA256），因此 knot-adapter 无需引入任何 AWS SDK。</p>
 *
 * <p>两个必须记住的坑：</p>
 * <ol>
 *   <li><b>时钟必须 UTC</b>：{@code X-Amz-Date} 与凭据作用域里的日期戳都按 UTC 取，
 *       用 {@code LocalDateTime.now()} 带本地时区会直接签名失败（AWS 时间窗 5 分钟）。</li>
 *   <li><b>签名覆盖的路径必须与实际发出的字节一致</b>：payload 的 SHA-256 与实际请求体
 *       必须是同一份字节（见 {@code AbstractUpstreamProtocolExecutor} 的签名钩子）；
 *       百分号编码只做一次（本类不再对已编码路径二次编码）。</li>
 * </ol>
 */
public final class AwsSigV4Support {

    public static final String ALGORITHM = "AWS4-HMAC-SHA256";
    public static final String TERMINATOR = "aws4_request";

    /** 空请求体的 SHA-256（AWS 固定常量，省掉一次摘要计算）。 */
    public static final String EMPTY_PAYLOAD_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private AwsSigV4Support() {
    }

    /** {@code X-Amz-Date} 头值：{@code yyyyMMdd'T'HHmmss'Z'}，按 UTC 取。 */
    public static String amzDate(Instant instant) {
        return AMZ_DATE.format(instant == null ? Instant.now() : instant);
    }

    /** 凭据作用域里的日期戳：{@code yyyyMMdd}，按 UTC 取。 */
    public static String dateStamp(Instant instant) {
        return DATE_STAMP.format(instant == null ? Instant.now() : instant);
    }

    public static String payloadHash(byte[] payload) {
        return payload == null || payload.length == 0 ? EMPTY_PAYLOAD_HASH : sha256Hex(payload);
    }

    public static String payloadHash(String payload) {
        return payload == null || payload.isEmpty()
                ? EMPTY_PAYLOAD_HASH
                : sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
    }

    /** 凭据作用域：{@code <dateStamp>/<region>/<service>/aws4_request}。 */
    public static String credentialScope(String dateStamp, String region, String service) {
        return dateStamp + "/" + region + "/" + service + "/" + TERMINATOR;
    }

    /**
     * 规范 URI：入参须是<b>已完成 RFC3986 百分号编码</b>的路径原文。
     *
     * <p>刻意不再二次编码：Bedrock 的模型 ID 含 {@code :}（如
     * {@code anthropic.claude-sonnet-4-20250514-v1:0}），路径在拼 URL 时已编成 {@code %3A}；
     * 这里再编一次会变成 {@code %253A}，与 AWS 侧按实际收到路径算出的规范 URI 不一致 → 全站 403。</p>
     */
    public static String canonicalUri(String encodedPath) {
        if (encodedPath == null || encodedPath.isBlank()) {
            return "/";
        }
        String path = encodedPath.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * 规范查询串：参数名与值各自 RFC3986 编码后按「名=值」字典序排序，以 {@code &} 连接。
     * 无查询串返回空串（AWS 规范里空查询是空串而不是 {@code /}）。
     */
    public static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            pairs.add(uriEncode(name) + "=" + uriEncode(value));
        }
        pairs.sort(Comparator.naturalOrder());
        return String.join("&", pairs);
    }

    /**
     * 规范头块：头名小写、值去首尾空白并把连续空白压成一个空格，按头名字典序逐行 {@code name:value\n}。
     */
    public static String canonicalHeaders(Map<String, String> headers) {
        Map<String, String> normalized = sortedLowerHeaders(headers);
        StringBuilder builder = new StringBuilder();
        normalized.forEach((name, value) -> builder.append(name).append(':').append(value).append('\n'));
        return builder.toString();
    }

    /** 参与签名的头名清单：小写、字典序、分号连接（{@code host;x-amz-date}）。 */
    public static String signedHeaders(Map<String, String> headers) {
        return String.join(";", sortedLowerHeaders(headers).keySet());
    }

    /**
     * 规范请求：方法 / 规范 URI / 规范查询串 / 规范头块 / 已签名头名 / 请求体哈希，六段以 {@code \n} 连接。
     */
    public static String canonicalRequest(String httpMethod,
                                         String canonicalUri,
                                         String canonicalQuery,
                                         Map<String, String> headers,
                                         String payloadHash) {
        return httpMethod + '\n'
                + canonicalUri + '\n'
                + canonicalQuery + '\n'
                + canonicalHeaders(headers) + '\n'
                + signedHeaders(headers) + '\n'
                + payloadHash;
    }

    /**
     * 待签串：算法标识 / {@code X-Amz-Date} / 凭据作用域 / 规范请求的 SHA-256，四段以 {@code \n} 连接。
     */
    public static String stringToSign(String amzDate, String credentialScope, String canonicalRequest) {
        return ALGORITHM + '\n'
                + amzDate + '\n'
                + credentialScope + '\n'
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算签名：先用 {@code AWS4+secretKey} 依次 HMAC 日期戳、region、service、
     * {@code aws4_request} 派生签名密钥，再对待签串做一次 HMAC-SHA256。
     */
    public static String signature(String secretKey,
                                   String dateStamp,
                                   String region,
                                   String service,
                                   String stringToSign) {
        byte[] key = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        key = hmacSha256(key, region);
        key = hmacSha256(key, service);
        key = hmacSha256(key, TERMINATOR);
        return toHexLower(hmacSha256(key, stringToSign));
    }

    /** {@code Authorization} 头值。 */
    public static String authorization(String accessKey,
                                       String credentialScope,
                                       String signedHeaders,
                                       String signature) {
        return ALGORITHM
                + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    /**
     * RFC3986 百分号编码：非 unreserved 字符（{@code A-Za-z0-9-_.~}）一律编码，十六进制用大写。
     * 与签名/摘要的小写十六进制不同——这是 AWS URI 编码的既定口径。
     */
    public static String uriEncode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int c = raw & 0xFF;
            boolean unreserved = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~';
            if (unreserved) {
                builder.append((char) c);
            } else {
                builder.append('%').append(HEX_UPPER[c >>> 4]).append(HEX_UPPER[c & 0x0F]);
            }
        }
        return builder.toString();
    }

    private static Map<String, String> sortedLowerHeaders(Map<String, String> headers) {
        Map<String, String> normalized = new TreeMap<>();
        if (headers == null) {
            return normalized;
        }
        headers.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                return;
            }
            normalized.put(name.toLowerCase(Locale.ROOT), collapseWhitespace(value));
        });
        return normalized;
    }

    private static String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHexLower(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String toHexLower(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte raw : data) {
            int c = raw & 0xFF;
            builder.append(HEX_LOWER[c >>> 4]).append(HEX_LOWER[c & 0x0F]);
        }
        return builder.toString();
    }
}
