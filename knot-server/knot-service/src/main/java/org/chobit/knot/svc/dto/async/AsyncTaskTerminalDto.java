package org.chobit.knot.svc.dto.async;

/**
 * 异步任务终态时随 CAS 一起落库的两段证据：上游原文 + 结算快照。
 *
 * <p>⭐ <b>为什么打成一个类型，而不是给 {@code complete} 再添两个 String</b>：
 * 两个相邻的 {@code String} 参数在调用点看起来一模一样，传反了编译器毫无信号，
 * 而传反的后果是「上游原文进了结算列」——读取侧解析失败后静默降级成 null，
 * 表现为「这笔任务从来就没有账」，排查时几乎不会往「参数顺序」上想。</p>
 *
 * <p>⚠ 两段都<b>不解析、不裁剪</b>：原文失真会让离线重算与线上口径分叉，
 * 那比「少存一点」严重得多。体积由上游响应本身决定（视频查询响应通常 &lt; 10KB）。</p>
 *
 * @param terminalBody   上游终态查询响应原文；没有响应（超时、快照不可读、查询异常）时为 {@code null}
 * @param settlementJson 结算快照（与同步链路 {@code knot_extend} 同形）；不计费路径为 {@code null}
 */
public record AsyncTaskTerminalDto(String terminalBody, String settlementJson) {

    /** 无证据（超时 / 快照不可读 / 未到终态）：两段都空，不写假值 */
    public static final AsyncTaskTerminalDto EMPTY = new AsyncTaskTerminalDto(null, null);

    /** 两段都为空：调用方据此跳过组装，但落库语句仍要执行（CAS 本身就是全量赋值） */
    public boolean isEmpty() {
        return terminalBody == null && settlementJson == null;
    }
}
