package org.chobit.knot.gateway.upstream.support;

import org.springframework.http.HttpHeaders;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上游（供应商）响应侧的请求标识解析：白名单抓取 + request-id 归一。
 *
 * <p><b>用途唯一</b>：网关与供应商联合排查（提工单时对方只认他们回的 id），
 * 不透给客户端、不做任何开关——见 {@code docs/上游trace打通-双向对账设计-2026-10-17.md} §1.1。</p>
 *
 * <p><b>为什么是白名单而不是全量抓</b>：上游响应头可能含 {@code set-cookie}、
 * 鉴权回显等敏感内容，全量快照会直接泄进数仓消息与日志。白名单只收
 * 「对账用标识」，新增头必须过 code review。</p>
 *
 * <p><b>request-id 与 log-id 取其一，优先 request-id</b>（robin 2026-10-19 拍板）：
 * 先按 request-id 类头优先序取「供应商侧请求 id」；request-id 类全部未命中时，
 * 兜底取 log-id 类头（字节系 {@code X-Tt-Logid} / Cloudflare {@code cf-ray}）并
 * <b>视为 request_id</b>。两者最终只产出<b>一个</b>上游标识 {@code upstream_request_id}，
 * 不再并存两个字段——联合排查宁要一个不完美的 id，也不要 null。
 * 头名确定性分级（均已实测确认，详见设计文档 §4.1）：request-id 类 {@code x-request-id} /
 * {@code request-id} / {@code apim-request-id} / {@code x-acs-request-id} /
 * {@code x-tc-request-id}（腾讯云 / 混元）/ {@code msh-request-id}（Moonshot 开放平台）；
 * log-id 类兜底 {@code x-tt-logid} / {@code cf-ray}。</p>
 *
 * <p><b>2026-10-23：必须排除「我方 id 的回显」</b>。网关会把自生成的 request_id 以
 * {@code X-Request-Id} 发给上游，而部分上游 / 反代（APIM、网关类中间件）会把这个请求头
 * <b>原样回显</b>到响应头里。若不排除，{@code upstream_request_id} 就会取到我方自己的 id，
 * 冒充成供应商侧编号——提工单时对方查不到，数仓里两列变成同一个值，双向对账彻底失效。
 * 因此两个入口都要求传入 {@code ownRequestId}：命中值与之相同即视为<b>未命中</b>，
 * 继续按优先序往下找（而不是直接返回 null，避免连带丢掉后面真存在的上游 id）。</p>
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
     * 命中值同样归一进 {@code upstream_request_id}（视为 request_id，见类注释）。
     * 字节系 / Cloudflare 基础设施 id，供应商工单直接检索价值有限，但聊胜于无。
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
     *
     * <p>值统一 trim 后存入；空值与未命中头一律跳过（返回的 map 可能为空，不为 null）。
     * 键为白名单声明名（小写），便于日志与数仓侧稳定解析。</p>
     *
     * <p>与 {@link #requestId(HttpHeaders, String)} 同口径排除我方 id 的回显：
     * 快照里若留着「我发的 id 被上游回显」的那一条，读者会误以为它是上游编号。</p>
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
     * 归一 request-id：按 {@link #REQUEST_ID_HEADERS} 优先序取第一个非空命中；
     * request-id 类全部未命中时，兜底取 {@link #LOG_ID_HEADERS} 并视为 request_id。
     * 全部未命中返回 null（不是空串——null 表示无数据，数仓侧按 IS NULL 区分）。
     *
     * <p>命中值等于 {@code ownRequestId} 的一律跳过：那是上游把我方请求头回显回来的，
     * 不是我方要采集的「供应商侧 id」。跳过而非返回 null，是为了不连带丢掉后面
     * 真实存在的上游 id（例如回显了 {@code x-request-id} 但同时回了 {@code x-tc-request-id}）。</p>
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
     * 该响应头值是否只是我方 request_id 的回显（忽略大小写与首尾空白）。
     *
     * <p>忽略大小写：我方发小写 hex，但上游/中间件可能规范化为大写再回显。</p>
     */
    private static boolean isEchoOfOwn(String value, String ownRequestId) {
        if (ownRequestId == null || ownRequestId.isBlank()) {
            return false;
        }
        return ownRequestId.trim().equalsIgnoreCase(value.trim());
    }
}
