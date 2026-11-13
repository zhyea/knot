package org.chobit.knot.gateway.runtime;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单个请求的 W3C 追踪上下文：traceparent 的解析、生成与回写，同时承载网关自生成的 {@code request_id}。
 * 上下文放在 Reactor Context（{@link #CONTEXT_KEY}）：{@code GatewayTraceFilter} 在入口写入，模板用 {@code Mono.deferContextual} 读取。
 * {@code request_id} 不写进 traceparent、不受入站 traceparent 影响；客户端传入的 request-id 类头一律忽略，网关是唯一权威。
 */
public final class GatewayTraceContext {

    /**
     * Reactor Context 键：本次请求的 {@link GatewayTraceContext}，由 {@code GatewayTraceFilter} 在入口 {@code contextWrite} 写入。
     */
    public static final String CONTEXT_KEY = "knot.trace";

    /**
     * 日志 MDC 键（{@code logging.pattern.level} 引用的就是这三个名字）；由 {@link MdcContextSupport} 在阻塞段的任务线程上重绑。
     */
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_TRACEPARENT = "traceparent";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{2})-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$"
    );

    private final String version;
    private final String traceId;
    private final String parentId;
    private final String spanId;
    private final String traceFlags;
    private final String traceparent;
    private final String originalTraceparent;
    private final boolean generatedTraceId;
    /**
     * 网关自生成的请求标识（32 位小写 hex）：构造时固定，整条链路唯一。
     * traceId 可能来自入站 traceparent，requestId 恒由网关生成，透传给上游后供应商侧可检索。
     */
    private final String requestId;

    private GatewayTraceContext(String version,
                                String traceId,
                                String parentId,
                                String spanId,
                                String traceFlags,
                                String originalTraceparent,
                                boolean generatedTraceId,
                                String requestId) {
        this.version = version;
        this.traceId = traceId;
        this.parentId = parentId;
        this.spanId = spanId;
        this.traceFlags = traceFlags;
        this.originalTraceparent = originalTraceparent;
        this.generatedTraceId = generatedTraceId;
        this.traceparent = version + "-" + traceId + "-" + spanId + "-" + traceFlags;
        // 传 null 表示「这是一次新请求，生成新的」；传值表示「沿用同一次逻辑请求的编号」
        this.requestId = requestId == null ? newRequestId() : requestId;
    }

    /**
     * Builds a gateway trace context from a W3C traceparent header.
     */
    public static GatewayTraceContext fromTraceparent(String traceparent) {
        String trimmed = StringUtils.trimToNull(traceparent);
        if (trimmed == null) {
            return generated(null);
        }
        Matcher matcher = TRACEPARENT_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return generated(trimmed);
        }
        String version = lower(matcher.group(1));
        String traceId = lower(matcher.group(2));
        String parentId = lower(matcher.group(3));
        String traceFlags = lower(matcher.group(4));
        if ("ff".equals(version) || isZero(traceId) || isZero(parentId)) {
            return generated(trimmed);
        }
        return new GatewayTraceContext(version, traceId, parentId, randomHex(8), traceFlags, trimmed, false, null);
    }

    /**
     * 按既有 traceparent 原样重放同一次逻辑请求的追踪上下文：沿用它的 span，并复用给定 requestId。
     * {@code spanId} 取 traceparent 的第 3 段（发起该次调用的父 span），异步任务的 1 + N 条事件共用这一个坐标，区分靠流量事件的 {@code task_query_seq}。
     *
     * @param traceparent 提交时快照下来的 traceparent（含提交那次的 span）
     * @param requestId   提交时的网关请求编号；{@code null}/空则新生成（脏数据兜底）
     */
    public static GatewayTraceContext replay(String traceparent, String requestId) {
        GatewayTraceContext base = fromTraceparent(traceparent);
        String inheritedRequestId = StringUtils.trimToNull(requestId);
        if (base.generatedTraceId) {
            // traceparent 不可解析（空/格式错/全零）：无法回到原 span，退化为一个全新的
            // 链路，但 requestId 仍沿用——它是「我方对这次请求的编号」，与 traceparent 是否合法无关
            return new GatewayTraceContext(base.version, base.traceId, base.parentId, base.spanId,
                    base.traceFlags, base.originalTraceparent, true, inheritedRequestId);
        }
        return new GatewayTraceContext(base.version, base.traceId, base.parentId, base.parentId,
                base.traceFlags, base.originalTraceparent, false, inheritedRequestId);
    }

    /**
     * 从 traceparent 头部解析 traceId：只做格式解析，缺失或非法时返回 {@code null}，不生成新 trace。
     */
    public static String traceIdOf(String traceparent) {
        String trimmed = StringUtils.trimToNull(traceparent);
        if (trimmed == null) {
            return null;
        }
        Matcher matcher = TRACEPARENT_PATTERN.matcher(trimmed);
        return matcher.matches() ? lower(matcher.group(2)) : null;
    }

    public String version() {
        return version;
    }

    public String traceId() {
        return traceId;
    }

    public String parentId() {
        return parentId;
    }

    public String spanId() {
        return spanId;
    }

    public String traceFlags() {
        return traceFlags;
    }

    public String traceparent() {
        return traceparent;
    }

    /**
     * 网关自生成的请求标识（32 位小写 hex，非空）：透传给上游的 {@code X-Request-Id}，也是数仓消息的 {@code request_id}。
     * 与 {@code upstream_request_id}（上游返回的 id）是两个东西，勿混用。
     */
    public String requestId() {
        return requestId;
    }

    public String originalTraceparent() {
        return originalTraceparent;
    }

    public boolean generatedTraceId() {
        return generatedTraceId;
    }

    /**
     * 把本追踪上下文写进当前线程的 MDC（日志 pattern 的 {@code %X{traceId}} 从这里取）。
     * 只在阻塞段任务线程上调用（见 {@link MdcContextSupport}）。
     */
    public void bindMdc() {
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);
        MDC.put(MDC_TRACEPARENT, traceparent);
    }

    /**
     * 清掉 {@link #bindMdc()} 写入的三个键，任务收尾时调用。
     */
    public void clearMdc() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
        MDC.remove(MDC_TRACEPARENT);
    }

    /**
     * 生成一个新的网关请求标识：128 位随机数的 32 位小写 hex 形态（与 traceId 同格式），不复用 traceId。
     */
    public static String newRequestId() {
        return randomHex(16);
    }

    private static GatewayTraceContext generated(String originalTraceparent) {
        return new GatewayTraceContext("00", randomHex(16), null, randomHex(8), "01", originalTraceparent, true, null);
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean isZero(String value) {
        return value.chars().allMatch(ch -> ch == '0');
    }

    private static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        do {
            RANDOM.nextBytes(bytes);
        } while (allZero(bytes));
        StringBuilder builder = new StringBuilder(byteCount * 2);
        for (byte item : bytes) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }

    private static boolean allZero(byte[] bytes) {
        for (byte item : bytes) {
            if (item != 0) {
                return false;
            }
        }
        return true;
    }
}
