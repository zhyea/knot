package org.chobit.knot.gateway.async;

/**
 * 一轮扫描的策略参数（批量、占位时长、保留窗口），由 app 侧从自己的配置装配后传入。
 * 不含 {@code concurrency}：并发由发起方（poller 的 {@code Flux.flatMap}）决定。
 *
 * @param batchSize        单批抢占上限
 * @param expireBatchSize  单批超时回收上限
 * @param claimTtlMs       占位有效期，必须严格大于查询超时（启动期由调用方校验）
 * @param retentionDays    终态行保留天数
 * @param cleanupBatchSize 单批清理上限
 */
public record AsyncTaskRunPolicy(int batchSize,
                                 int expireBatchSize,
                                 long claimTtlMs,
                                 int retentionDays,
                                 int cleanupBatchSize) {
}
