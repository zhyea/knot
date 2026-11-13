package org.chobit.knot.adapter.support;

import org.apache.commons.lang3.StringUtils;

/**
 * SSE 行语法原语：{@code data:} / {@code event:} 前缀的读写与 {@code [DONE]} 标记判断，
 * 与供应商和 AI 协议无关。字节流切行（跨 chunk 半行、UTF-8 边界、1MB 上行保护）归
 * {@code ProxyStreamingFlux}，本类只管「一行 SSE 文本长什么样」。
 */
public final class SseLineSupport {

    /** SSE 事件分隔符：空行。多行合成时必须显式补上，否则两个事件黏成一条。 */
    public static final String BLANK = "";

    private static final String DATA_PREFIX = "data:";
    private static final String EVENT_PREFIX = "event:";
    private static final String DONE_MARKER = "[DONE]";

    private SseLineSupport() {
    }

    /** 该行是否为 {@code data:} 行（忽略前后空白）。 */
    public static boolean isData(String line) {
        return StringUtils.trim(line).startsWith(DATA_PREFIX);
    }

    /** 该行是否为 {@code event:} 行（忽略前后空白）。 */
    public static boolean isEvent(String line) {
        return StringUtils.trim(line).startsWith(EVENT_PREFIX);
    }

    /**
     * 取 {@code data:} 之后的载荷（已 trim）；非 data 行返回 {@code null}。
     */
    public static String dataOf(String line) {
        String trimmed = StringUtils.trim(line);
        if (!trimmed.startsWith(DATA_PREFIX)) {
            return null;
        }
        return StringUtils.trim(trimmed.substring(DATA_PREFIX.length()));
    }

    /** 取 {@code event:} 之后的事件名（已 trim）；非 event 行返回 {@code null}。 */
    public static String eventName(String line) {
        String trimmed = StringUtils.trim(line);
        if (!trimmed.startsWith(EVENT_PREFIX)) {
            return null;
        }
        return StringUtils.trim(trimmed.substring(EVENT_PREFIX.length()));
    }

    /** 该行是否为流结束标记 {@code data: [DONE]}。 */
    public static boolean isDone(String line) {
        return DONE_MARKER.equals(dataOf(line));
    }

    /** 构造 {@code data:} 行。 */
    public static String data(String payload) {
        return DATA_PREFIX + " " + payload;
    }

    /** 构造 {@code event:} 行。 */
    public static String event(String name) {
        return EVENT_PREFIX + " " + name;
    }

    /** 流结束标记行的载荷 {@code [DONE]}（供合成方向复用）。 */
    public static String doneMarker() {
        return DONE_MARKER;
    }
}
