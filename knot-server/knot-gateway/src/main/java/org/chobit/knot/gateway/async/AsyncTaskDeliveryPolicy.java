package org.chobit.knot.gateway.async;

/**
 * 回调交付的运行时策略（批量、租约、重试），由 app 侧从自己的配置装配后传入。
 *
 * <p>⭐ 与 {@link AsyncTaskRunPolicy} 分开是两个理由：</p>
 * <ul>
 *   <li><b>生命周期不同</b>：轮询参数是「多久问一次上游」，交付参数是「多久把结果推给客户端」，
 *       两者可以独立调（回调慢不该逼着改轮询节拍）；</li>
 *   <li><b>误配后果不同</b>：轮询租约配小了会重复调上游，交付租约配小了会重复回调客户——
 *       后者是对外可见的缺陷，收敛到一份独立的值对象里，改它时不容易顺手改错另一组。</li>
 * </ul>
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
