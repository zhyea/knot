package org.chobit.knot.gateway.upstream.support;

import org.springframework.http.HttpHeaders;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上游（供应商）响应侧的请求标识解析：白名单抓取 + request-id 归一；只收「对账用标识」，新增头必须过 code review。
 * 归一规则：按 request-id 类头优先序取第一个非空命中，全部未命中时兜底取 log-id 类头（{@code X-Tt-Logid} / {@code cf-ray}），最终只产出一个 {@code upstream_request_id}。
 * 用途唯一：网关与供应商联合排查，不透给客户端、不做任何开关。
 * 两个入口都必须传 {@code ownRequestId}：命中值与之相同（忽略大小写与首尾空白）即视为未命中，继续按优先序往下找。
 */
public final class UpstreamRequestIdResolver {

    /**
     * request-id 类头的归一优先序：不同供应商用不同头名承载同一个概念
     * （「我方系统里这次请求的 id」），取第一个非空命中。
     */
    private static final String[] REQUEST_ID_HEADERS = {
            "x-request-id",      // OpenAI 及多数 OpenAI 兼容厂商（DeepSeek / MiniMax 等）
            "request-id",        // Anthropic、智谱
            "apim-request-id",   // Azure OpenAI（APIM 出口）
            "x-acs-request-id",  // 阿里云百炼 / DashScope
            "x-tc-request-id",   // 腾讯云 API 3.0（混元大模型，响应头 X-TC-RequestId）
            "msh-request-id",    // Moonshot 开放平台（Kimi 大模型，响应头 msh-request-id）
    };

    /**
     * log-id 类头的兜底序：仅当 {@link #REQUEST_ID_HEADERS} 全部未命中时启用，
     * 命中值同样归一进 {@code upstream_request_id}（视为 request_id）。
     */
    private static final String[] LOG_ID_HEADERS = {
            "x-tt-logid",     // 火山方舟 / 字节系（响应头 X-Tt-Logid）
            "cf-ray",         // Cloudflare 出口（自建/反代链路）
    };

    /** 上游回显的 W3C traceparent：极少数厂商会回带，留作交叉验证。 */
    private static final String TRACEPARENT = "traceparent";

    private UpstreamRequestIdResolver() {
    }

    /**
     * 白名单快照：只含白名单（request-id 类 + log-id 类）+ traceparent 的命中项。
     * 值统一 trim 后存入，空值与未命中头一律跳过（返回的 map 可能为空，不为 {@code null}）；键为白名单声明名（小写）。
     * 与 {@link #requestId(HttpHeaders, String)} 同口径排除我方 id 的回显。
     *
     * @param ownRequestId 我方本次请求的 request_id（可为 null，表示不排除）
     */
    public static Map<String, String> snapshot(HttpHeaders headers, String ownRequestId) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        if (headers == null) {
            return snapshot;
        }
        collect(snapshot, headers, ownRequestId, REQUEST_ID_HEADERS);
        collect(snapshot, headers, ownRequestId, LOG_ID_HEADERS);
        put(snapshot, headers, ownRequestId, TRACEPARENT);
        return snapshot;
    }

    /**
     * 归一 request-id：按 {@link #REQUEST_ID_HEADERS} 优先序取第一个非空命中；request-id 类全部未命中时，兜底取 {@link #LOG_ID_HEADERS} 并视为 request_id。
     * 全部未命中返回 {@code null}（不是空串——{@code null} 表示无数据，数仓侧按 IS NULL 区分）。
     * 命中值等于 {@code ownRequestId} 的一律跳过（那是上游回显的我方 id），继续按优先序往下找而不是直接返回 {@code null}。
     *
     * @param ownRequestId 我方本次请求的 request_id（可为 null，表示不排除）
     */
    public static String requestId(HttpHeaders headers, String ownRequestId) {
        String requestId = first(headers, ownRequestId, REQUEST_ID_HEADERS);
        if (requestId != null) {
            return requestId;
        }
        return first(headers, ownRequestId, LOG_ID_HEADERS);
    }

    private static void collect(Map<String, String> target,
                                HttpHeaders headers,
                                String ownRequestId,
                                String... names) {
        for (String name : names) {
            put(target, headers, ownRequestId, name);
        }
    }

    private static void put(Map<String, String> target,
                            HttpHeaders headers,
                            String ownRequestId,
                            String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank() || isEchoOfOwn(value, ownRequestId)) {
            return;
        }
        target.put(name, value.trim());
    }

    private static String first(HttpHeaders headers, String ownRequestId, String... names) {
        if (headers == null) {
            return null;
        }
        for (String name : names) {
            String value = headers.getFirst(name);
            if (value != null && !value.isBlank() && !isEchoOfOwn(value, ownRequestId)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 该响应头值是否只是我方 request_id 的回显：比较时忽略大小写与首尾空白。
     */
    private static boolean isEchoOfOwn(String value, String ownRequestId) {
        if (ownRequestId == null || ownRequestId.isBlank()) {
            return false;
        }
        return ownRequestId.trim().equalsIgnoreCase(value.trim());
    }
}
