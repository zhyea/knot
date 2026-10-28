package org.chobit.knot.gateway.async;

/**
 * 异步任务状态机的<b>事件</b>：一次轮询（或一次运维动作）施加给任务的、可能改变状态的输入。
 *
 * <p>⭐ <b>错误码挂在事件上，不散在处理分支里</b>。原来 {@code "UPSTREAM_TASK_FAILED"} /
 * {@code "TASK_TIMEOUT"} 这些字面量分别写在 4 个 if 分支里，加一种失败原因就要再想一遍「码该叫什么、
 * 要不要计费」；收到事件枚举上以后，一个事件自带「落哪个码、默认什么话术」，
 * 分支里只剩「怎么把数据取出来」。</p>
 *
 * <p>取值口径（{@link #errorCode()} 为 null 表示这个事件不代表一次错误）：
 * <ul>
 *   <li>{@code POLL_RUNNING} / {@code POLL_SUCCEEDED}：没出错，没有错误码；</li>
 *   <li>{@code QUERY_ERROR}：<b>出错了但不落终态</b>——码只进事件用于上报（数仓要能算
 *       「重试了几次才成功」），<b>不写 {@code error_code} 列</b>；</li>
 *   <li>其余：一律落终态且不计费，自带错误码并写入 {@code error_code} 列。</li>
 * </ul>
 *
 * <p>⚠ 所以「有码」与「落错误终态」<b>不是同一件事</b>：落库由 {@code Action} 决定，
 * 有码只是说明这个事件代表一次失败。把两者混为一谈会逼出「QUERY_ERROR 不许带码」这种
 * 看似严谨、实则让中间失败彻底不可见的约束。</p>
 */
public enum AsyncTaskEvent {

    /** 上游仍在跑（查询成功但未出结果）——留在在途态，退避后再查 */
    POLL_RUNNING(null, null),

    /** 上游出结果且产物齐全——唯一会计费的事件 */
    POLL_SUCCEEDED(null, null),

    /** 上游报失败 */
    POLL_FAILED("UPSTREAM_TASK_FAILED", "upstream task failed"),

    /** 上游说成功但取不到产物：不能按成功计费（钱收了没有货） */
    RESULT_MISSING("UPSTREAM_RESULT_MISSING", "upstream reported success without result url"),

    /**
     * 查询本身失败（网络 / 5xx / 响应体不可解析）——瞬时失败，退避重试，<b>不判终态</b>。
     *
     * <p>带码是为了让中间失败在数仓里可见：一次异步任务查询 20 次、其中 3 次 5xx，
     * 这个事实必须能算出来，否则「上游稳定性」这个指标对异步链路完全盲区。</p>
     */
    QUERY_ERROR("UPSTREAM_QUERY_ERROR", "upstream query failed, will retry"),

    /** 轮询次数用尽 */
    ATTEMPTS_EXHAUSTED("TASK_TIMEOUT", "polling attempts exceeded"),

    /** knot 侧超时（{@code expires_at} 到期） */
    TIMEOUT("TASK_TIMEOUT", "async task exceeded knot side timeout"),

    /** 提交时落库的 {@code poll_spec_json} / {@code context_json} 不可解析 */
    SNAPSHOT_INVALID("TASK_SNAPSHOT_INVALID", "task snapshot (spec/context) cannot be parsed"),

    /** 主动取消（{@code kb_async_tasks.status} 已留 CANCELLED，本轮尚未开放入口） */
    CANCEL("TASK_CANCELLED", "cancelled by operator");

    private final String errorCode;
    private final String defaultMessage;

    AsyncTaskEvent(String errorCode, String defaultMessage) {
        this.errorCode = errorCode;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 本事件代表的错误码；{@code null} 表示没出错。
     *
     * <p>落终态的事件由 {@code finish} 写入 {@code kb_async_tasks.error_code}；
     * 中间事件（{@link #QUERY_ERROR}）的码<b>只进流量事件</b>，不落库——
     * 它没有落终态，任务还会继续查。</p>
     */
    public String errorCode() {
        return errorCode;
    }

    /** 兜底错误话术；上游给了更具体的原因时由调用方覆盖 */
    public String defaultMessage() {
        return defaultMessage;
    }
}
