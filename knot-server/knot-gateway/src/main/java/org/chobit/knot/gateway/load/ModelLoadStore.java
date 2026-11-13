package org.chobit.knot.gateway.load;

import java.util.List;
import java.util.Map;

/**
 * 模型「在途并发」计量存储：最空闲选模（{@code LEAST_LOAD}）的负载数据源，与 {@link org.chobit.knot.gateway.traffic.TrafficCounterStore} 的窗口单调累加不同，本接口是可增可减、随时可读的 gauge，不共用键空间。
 * 单节点实现 {@code CaffeineModelLoadStore}（节点内近似），多节点实现 {@code RedisModelLoadStore}（全局口径），由 {@code knot.load.store} 选择。
 * {@code redis} 档 fail-hard：Redis 不可用抛 {@code RedisUnavailableException} → 网关 503；键不存在返回 {@code 0} 是正常语义。
 */
public interface ModelLoadStore {

    /**
     * 存储实现标识，用于启动日志与排查（{@code caffeine} / {@code redis}）。
     */
    String id();

    /**
     * 在途 +1，并刷新该键的存活时间；返回累加后的在途数。
     * 刷新 TTL 为滑动语义；键的存活时间是计数泄漏的上界，残留计数最多存活一个 TTL。
     */
    long enter(String key);

    /**
     * 在途 −1，返回递减后的在途数；键不存在时返回 {@code 0}，不创建条目。
     * 下限为 {@code 0}，迟到递减不压出负数；不刷新 TTL。
     */
    long leave(String key);

    /**
     * 读取当前在途数，不存在则返回 {@code 0}；不创建条目。
     */
    long inflight(String key);

    /**
     * 批量读取当前在途数：一次返回多个键的当前值，缺失或脏数据的键返回 {@code 0}。
     * 用于选路热路径，{@code redis} 档走 {@code MGET} 单次取 N 个键；语义与 {@link #inflight} 一致，键集合为空返回空 Map。
     * 返回的 Map 仅用于读，写入仍走 {@link #enter} / {@link #leave} 的单键 Lua。
     *
     * @param keys 键集合（可空 / 空）
     * @return 以入参键为键、当前在途数为值的 Map；缺失或脏数据对应值为 {@code 0}
     */
    Map<String, Long> inflightBatch(List<String> keys);
}
