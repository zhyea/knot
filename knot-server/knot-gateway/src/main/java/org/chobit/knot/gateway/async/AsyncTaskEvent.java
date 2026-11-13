package org.chobit.knot.gateway.async;

/**
 * 异步任务状态机的事件：一次轮询（或一次运维动作）施加给任务的、可能改变状态的输入。
 * 错误码挂在事件上；{@link #errorCode()} 为 null 表示该事件不代表一次错误。
 * {@code POLL_RUNNING} / {@code POLL_SUCCEEDED} 无错误码；{@code QUERY_ERROR} 出错但不落终态、码只进事件不写 {@code error_code} 列；其余落终态且不计费，自带错误码写入 {@code error_code} 列。
 * 落库由 {@code Action} 决定，「有码」与「落错误终态」不是同一件事。
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
     * 查询本身失败（网络 / 5xx / 响应体不可解析）——瞬时失败，退避重试，不判终态，带错误码供中间失败可见。
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
     * 落终态的事件由 {@code finish} 写入 {@code kb_async_tasks.error_code}；中间事件（{@link #QUERY_ERROR}）的码只进流量事件，不落库。
     */
    public String errorCode() {
        return errorCode;
    }

    /** 兜底错误话术；上游给了更具体的原因时由调用方覆盖 */
    public String defaultMessage() {
        return defaultMessage;
    }
}
