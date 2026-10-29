package org.chobit.knot.gateway.async;

/**
 * 一轮扫描的策略参数（批量、占位时长、保留窗口），由 app 侧从自己的配置装配后传入。
 *
 * <p>⭐ <b>为什么参数以值对象传入而不让 svc 读配置</b>：这些旋钮的 key 是
 * {@code knot.gateway.async-task.*}——那是<b>部署</b>口径（哪个进程跑、跑多大批量），
 * 而 knot-service 是 admin 与 gateway 共用的层，绑上 gateway 的配置前缀会让网关的部署参数
 * 变成两个 app 的隐式契约。传值对象则：svc 只声明「我需要哪些数」，谁配、默认值是谁的口径、
 * 改哪个 yml，全部留在装配处（{@code AsyncTaskConfiguration}），一处可见。</p>
 *
 * <p>⚠ 这里<b>没有</b> {@code concurrency}：并发是「同时发几个查询」的调度决策，
 * 由发起方（poller 的 {@code Flux.flatMap}）决定。把并发写进编排类会让它依赖 Reactor，
 * 而「一条任务怎么推进」一旦知道线程模型，用例就得起调度器才跑得起来。</p>
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
