package org.chobit.knot.gateway.async;

/**
 * 上游查询端口：异步任务的 N 次查询从这里出去。
 * 实现方不要把异常吞掉：抛出即被编排层当成「瞬时失败」（退避后重试，不判终态）。
 * 上游明确返回的业务失败（例如任务已失败）不是靠异常表达，而是查询成功后由响应体按档案的 {@code status_failed} 表达式判定。
 */
public interface AsyncTaskQueryPort {

    /**
     * 发起一次查询。
     *
     * @param context        任务快照里的上下文（含 {@code traceparent}，实现方自行取用，
     *                       不额外传参——同一个事实只有一个来源）
     * @param spec           档案快照（查询方法、路径、超时）
     * @param upstreamTaskId 上游任务 ID
     * @return 上游响应原文
     * @throws RuntimeException 瞬时失败（网络 / 5xx / 限流）：编排层退避重试，不判终态
     */
    String query(AsyncTaskContext context, AsyncTaskSpec spec, String upstreamTaskId);
}
