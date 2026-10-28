package org.chobit.knot.gateway.async;

import org.chobit.knot.dal.entity.AsyncTaskEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步任务存储。
 *
 * <p><b>DB 是唯一真相源</b>：不再以 Redis / 进程内存为主存储。缓存不可用不应放大成
 * 「上游异步任务永久丢失、永远不计费」——那条路的终点是资损且无从察觉。
 * 代价是每次 create 多一次 INSERT、每次轮询多一次 UPDATE，对视频这类分钟级任务完全可以接受。</p>
 */
public interface AsyncTaskStore {

    /** 落一条新任务（在途，等待首次轮询） */
    void insert(AsyncTaskEntity task);

    AsyncTaskEntity getByTaskCode(String taskCode);

    /**
     * 抢占一批到期任务：返回被本调用抢到的行（空列表表示本轮无到期任务）。
     *
     * <p>多节点同时 tick 时靠 DB 行锁互斥：一条 {@code UPDATE ... ORDER BY ... LIMIT} 完成抢占，
     * 再按占位号回查。节点宕机不释放占位，靠 {@code claim_expires_at} 到期自动回收——
     * 于是优雅停机不需要任何额外钩子。</p>
     */
    List<AsyncTaskEntity> claim(String claimId, Duration claimTtl, int limit);

    /**
     * 抢占一批<b>已过 knot 侧超时</b>的在途任务（交给上层按 {@code TIMEOUT} 落终态）。
     *
     * <p>⭐ 超时回收也要先占位，不能「读到就直接改」：读到的那一刻另一个节点可能正在处理这行
     * （占位有效），此时把它按超时收掉，等于用一个过期结论覆盖一个即将得出的真实结论。
     * 占位到手的节点才敢说「这行的终态由我来定」。</p>
     *
     * <p>与 {@link #claim} 是互斥的两个入口：{@code claim} 只挑 {@code expires_at} 未到的行，
     * 本方法只挑已到的行，一行在同一时刻只会走其中一条路——不必依赖两个节拍的先后顺序。</p>
     */
    List<AsyncTaskEntity> claimExpired(String claimId, Duration claimTtl, int limit);

    /**
     * 非终态：安排下一次轮询并释放占位。
     *
     * @param claimId 本次处理持有的占位号；<b>必须与行上的一致</b>，否则不写
     * @return 是否写入成功；{@code false} 表示占位已易主（本节点处理超时），调用方不得再当自己是主人
     */
    boolean scheduleNext(Long id, String claimId, LocalDateTime nextPollAt);

    /**
     * 落终态：CAS，只有在途态<b>且仍持有占位</b>才更新。
     *
     * @param claimId 本次处理持有的占位号
     * @return 是否由本调用落定；false 表示已被别的节点落过（或占位已易主），
     * 调用方不得再发终态事件——终态事件的口径是「一条任务一条」，由真正落定的人发
     */
    boolean complete(Long id, String claimId, String status, String resultJson,
                     String errorCode, String errorMessage);

    /** 清理归档窗口之前的终态行 */
    int cleanup(LocalDateTime before, int limit);
}
