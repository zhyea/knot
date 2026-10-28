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
 *
 * <p>2026-10-23：本上下文<b>同时承载网关自生成的 {@code request_id}</b>（{@link #requestId()}）。
 * 它和 trace 一样是「每请求一个、随链路传递」的东西，因此共用一个载体、一次
 * {@code contextWrite} 即可贯穿到上游请求头与数仓消息；但语义上它<b>不属于 W3C trace</b>——
 * 不写进 traceparent、不受入站 traceparent 影响（外部 traceId 再长也是别人的 id）。
 * 客户端传入的 request-id 类头一律忽略，网关是唯一权威。</p>
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
    /**
     * 网关自生成的请求标识（32 位小写 hex）：构造时固定，整条链路唯一。
     *
     * <p>与 traceId 的区别：traceId 可能来自<b>入站</b> traceparent（别人的链路 id），
     * requestId 恒由网关生成，是「我方对这次请求的编号」，透传给上游后供应商侧可检索。</p>
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
     * 按既有 traceparent <b>原样重放</b>同一次逻辑请求的追踪上下文：沿用它的 span，并复用给定 requestId。
     *
     * <p>⭐ <b>为什么需要这个入口</b>：{@link #fromTraceparent} 的语义是「解析入站 trace + 为<b>本次</b>请求
     * 生成一个新 span」（{@code spanId = randomHex(8)}）。用它去重建<b>已经发生过的那次请求</b>，
     * traceId 虽然对得上，spanId 与 requestId 每次都会变 —— 异步任务的 N 次查询各发一条流量事件，
     * 就会变成「同一笔任务、N 个不同 span」，数仓按 {@code (trace_id, span_id)} 找不到一组。</p>
     *
     * <p>语义澄清（避免误读成 bug）：这里 spanId 取 traceparent 的第 3 段，而 {@link #parentId()} 也叫这个名字。
     * <b>不是笔误</b>——W3C traceparent 的第 3 段本就是「发起这次调用的父 span」，把它复用为本次的 span，
     * 正是「回到同一次请求」这个动作。异步任务的 1 + N 条事件共用这一个坐标，
     * 代价是它们不再是 N 个可区分 span（要区分用流量事件里的 {@code task_query_seq}）。</p>
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

    /**
     * 网关自生成的请求标识（32 位小写 hex，非空）：透传给上游的 {@code X-Request-Id}，
     * 也是数仓消息的 {@code request_id}。
     *
     * <p>⚠ 与 {@code upstream_request_id}（上游返回的 id）是两个东西，勿混用。</p>
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

    /**
     * 生成一个新的网关请求标识：128 位随机数的 32 位小写 hex 形态（与 traceId 同格式）。
     *
     * <p>不用 UUID 带横线形态：项目内 traceId 已是 32 位 hex，日志检索与人工比对
     * 统一一种形状更省事；这里也不复用 traceId——traceId 可能继承自入站
     * traceparent（别人的 id），而 requestId 必须是我方自己的编号。</p>
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
