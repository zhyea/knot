package org.chobit.knot.gateway.async;

/**
 * 上游查询端口：异步任务的 N 次查询从这里出去。
 *
 * <p>⭐ <b>为什么是端口而不是直接调上游</b>：发一次查询要用 Reactor 的 {@code WebClient}、渠道凭据、
 * 签名与 URL 拼装——那些是 {@code knot-adapter} / gateway 运行时的事。<b>编排</b>（何时查、
 * 查失败算不算失败、退避多久、终态怎么落）是业务的事。把两者绑在一起，等于「改动轮询节奏」
 * 和「换 HTTP 客户端」变成同一件事。</p>
 *
 * <p>⚠ 实现方<b>不要把异常吞掉</b>：抛出即被编排层当成「瞬时失败」（退避后重试，不判终态）。
 * 上游明确返回的业务失败（例如任务已失败）不是靠异常表达的——那是查询成功、
 * 由响应体按档案的 {@code status_failed} 表达式判定。两者混淆的后果是
 * 「上游说失败」被当成网络抖动，任务一直被重试到超时。</p>
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
