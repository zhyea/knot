package org.chobit.knot.gateway.load;

/**
 * 模型「在途并发」计量存储：最空闲选模（{@code LEAST_LOAD}）的负载数据源。
 *
 * <p>与 {@link org.chobit.knot.gateway.traffic.TrafficCounterStore} 的关键差别：频控计数是
 * <b>窗口内单调累加</b>（只增、靠换窗口清零），本接口是 <b>gauge</b>（可增可减、随时可读），
 * 语义不同故不复用同一抽象，也不共用键空间。</p>
 *
 * <p>已提供两种实现：单节点走 {@code CaffeineModelLoadStore}，多节点走
 * {@code RedisModelLoadStore}，由 {@code knot.load.store} 选择。多节点下 caffeine 只能看到
 * 本节点在途，是<b>近似口径</b>；要真正的全局口径必须切 redis。</p>
 *
 * <p>故障一律 fail-open：读不到在途数时按 {@code 0}（不拥塞）处理，绝不阻断选路。</p>
 */
public interface ModelLoadStore {

    /**
     * 存储实现标识，用于启动日志与排查（{@code caffeine} / {@code redis}）。
     */
    String id();

    /**
     * 在途 +1，并刷新该键的存活时间；返回累加后的在途数。
     *
     * <p>刷新 TTL 是滑动语义：只要还有请求在打这个模型，条目就不会中途消失。
     * 键的存活时间同时是「计数泄漏」的上界——进程崩溃没来得及 {@link #leave} 时，
     * 残留计数最多存活一个 TTL。</p>
     */
    long enter(String key);

    /**
     * 在途 −1，返回递减后的在途数；键不存在时返回 {@code 0}，不创建条目。
     *
     * <p>下限为 {@code 0}：缓存过期 / 进程重启导致条目消失后的迟到递减不能把在途数压成负数，
     * 否则该模型会被当成「最空闲」而吸走全部流量。</p>
     *
     * <p>不刷新 TTL——递减不应延长条目寿命，否则泄漏的计数会被不断续命。</p>
     */
    long leave(String key);

    /**
     * 读取当前在途数，不存在则返回 {@code 0}；不创建条目。
     */
    long inflight(String key);
}
