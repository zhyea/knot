package org.chobit.knot.gateway.runtime;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单个请求的 W3C 追踪上下文：traceparent 的解析、生成与回写。
 *
 * <p>2026-10-20（WebFlux 批2）：<b>去掉 ThreadLocal</b>。线程本地属性在 Reactor 线程模型下
 * 无处安放（同一请求会跨多个线程），改为放进 <b>Reactor Context</b>
 * （{@link #CONTEXT_KEY}）：{@code GatewayTraceFilter} 在入口写入一次，
 * 模板用 {@code Mono.deferContextual} 读取，链路任何位置都能取到同一个 trace。</p>
 */
public final class GatewayTraceContext {

    /**
     * Reactor Context 键：本次请求的 {@link GatewayTraceContext}。
     *
     * <p>由 {@code GatewayTraceFilter} 在入口 {@code contextWrite} 写入；
     * 这是请求级追踪信息在链路里的唯一传递方式（不再有 ThreadLocal）。</p>
     */
    public static final String CONTEXT_KEY = "knot.trace";

    /**
     * 日志 MDC 键（{@code logging.pattern.level} 里引用的就是这三个名字）。
     *
     * <p>2026-10-20（WebFlux 批2）：MDC 本身仍是线程本地，跨线程不会自己传播——
     * 由 {@link MdcContextSupport} 在每个阻塞段的任务线程上重绑。</p>
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

    private GatewayTraceContext(String version,
                                String traceId,
                                String parentId,
                                String spanId,
                                String traceFlags,
                                String originalTraceparent,
                                boolean generatedTraceId) {
        this.version = version;
        this.traceId = traceId;
        this.parentId = parentId;
        this.spanId = spanId;
        this.traceFlags = traceFlags;
        this.originalTraceparent = originalTraceparent;
        this.generatedTraceId = generatedTraceId;
        this.traceparent = version + "-" + traceId + "-" + spanId + "-" + traceFlags;
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
        return new GatewayTraceContext(version, traceId, parentId, randomHex(8), traceFlags, trimmed, false);
    }

    /**
     * 从 traceparent 头部解析 traceId：只做格式解析，<b>缺失或非法时返回 {@code null}</b>，
     * 不生成新 trace。
     *
     * <p>2026-10-20：原先 {@code UpstreamProxyClient} 从线程本地取 traceId，但上游代理调用跑在
     * 异步线程上，线程本地永远为空 → 每次都生成一个与请求链路<b>无关的随机 traceId</b>。
     * 改为从上游请求携带的 traceparent（已由模板归一）解析。</p>
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

    public String originalTraceparent() {
        return originalTraceparent;
    }

    public boolean generatedTraceId() {
        return generatedTraceId;
    }

    /**
     * 把本追踪上下文写进当前线程的 MDC（日志pattern 的 {@code %X{traceId}} 从这里取）。
     *
     * <p>只在<b>阻塞段任务线程</b>上调用（见 {@link MdcContextSupport}）：Netty 的 event loop
     * 线程被成百上千个请求复用，在那里写 MDC 会把 A 请求的 traceId 印到 B 请求的日志上。</p>
     */
    public void bindMdc() {
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);
        MDC.put(MDC_TRACEPARENT, traceparent);
    }

    /**
     * 清掉 {@link #bindMdc()} 写入的三个键（任务收尾时调用，避免线程复用时残留上一条请求的 traceId）。
     */
    public void clearMdc() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
        MDC.remove(MDC_TRACEPARENT);
    }

    private static GatewayTraceContext generated(String originalTraceparent) {
        return new GatewayTraceContext("00", randomHex(16), null, randomHex(8), "01", originalTraceparent, true);
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
