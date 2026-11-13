package org.chobit.knot.gateway.async;

/**
 * 回调交付的运行时策略（批量、租约、重试），由 app 侧从自己的配置装配后传入。
 *
 * @param batchSize       单批抢占待投递上限
 * @param claimTtlMs      交付占位有效期，必须严格大于单次回调超时（启动期由调用方校验）
 * @param maxAttempts     最大尝试次数（含首次）；到上限落交付终态 FAILED
 * @param backoffBaseMs   退避基数（毫秒）
 * @param backoffMaxMs    退避封顶（毫秒）
 */
public record AsyncTaskDeliveryPolicy(int batchSize,
                                      long claimTtlMs,
                                      int maxAttempts,
                                      int backoffBaseMs,
                                      int backoffMaxMs) {
}
