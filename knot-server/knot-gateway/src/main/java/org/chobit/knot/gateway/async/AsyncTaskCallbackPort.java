package org.chobit.knot.gateway.async;

import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;

/**
 * 客户端回调端口：把任务终态推给客户端的 {@code callback_url}。
 * 投递失败一律抛异常，由编排层决定退避重试或到上限放弃；实现方不要把失败吞成返回值。
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
