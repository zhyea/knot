package org.chobit.knot.gateway.runtime;

import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GatewayTraceContext {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{2})-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$"
    );
    private static final ThreadLocal<GatewayTraceContext> CURRENT = new ThreadLocal<>();

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
     * Returns the current request trace context or builds one from the supplied traceparent.
     */
    public static GatewayTraceContext currentOrCreate(String traceparent) {
        GatewayTraceContext current = CURRENT.get();
        return current == null ? fromTraceparent(traceparent) : current;
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
     * Binds the trace context to the current thread.
     */
    public static void setCurrent(GatewayTraceContext context) {
        if (context == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(context);
    }

    /**
     * Clears the current thread trace context.
     */
    public static void clearCurrent() {
        CURRENT.remove();
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
