package org.chobit.knot.gateway.async;

import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;

/**
 * 客户端回调端口：把任务终态<b>推给</b>客户端的 {@code callback_url}。
 *
 * <p>⭐ <b>为什么是端口</b>：发一次回调要用 {@code WebClient}、超时、HTTP 状态判定——
 * 那些是网关运行时的事；「投递成功了没、失败要不要退避、几次放弃」是编排的事。
 * 绑在一起，等于「换 HTTP 客户端」和「改重试策略」变成同一件事（与
 * {@link AsyncTaskQueryPort} 同一套理由）。</p>
 *
 * <p>⚠ <b>失败一律抛异常</b>：编排层据异常决定「退避重试」还是「到上限放弃」。
 * 实现方<b>不要</b>把失败吞成返回值——那会让编排层以为投递成功，一条都没重试，
 * 客户的回调就静默丢了。</p>
 */
public interface AsyncTaskCallbackPort {

    /**
     * 投递一次回调。
     *
     * @param callbackUrl 目标地址（已通过 {@link AsyncTaskCallbackUrlSupport} 校验）
     * @param payload     回调 body（与轮询响应同形，见 {@link AsyncTaskViewSupport}）
     * @throws RuntimeException 投递失败（连接 / 超时 / 非 2xx）
     */
    void deliver(String callbackUrl, AsyncTaskQueryResponse payload);
}
