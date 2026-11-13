package org.chobit.knot.svc.dto.async;

/**
 * 异步任务终态时随 CAS 一起落库的两段证据：上游原文 + 结算快照。
 *
 * 两段都原样存储，不解析、不裁剪。
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
