package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.AsyncTaskEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步任务实例读写。
 *
 * <p>⚠ {@code claim} 是多节点不重复轮询的唯一闸门：一条 {@code UPDATE ... ORDER BY ... LIMIT}
 * 借行锁完成抢占，再按 {@code claimId} 回查被自己抢到的行。不引分布式锁中间件 ——
 * 「待轮询集合」就是这张表，抢占就是一次原子更新。</p>
 *
 * <p>⭐ 抢到只是「拿到租约」，<b>每一次写都必须带着它</b>（{@code scheduleNext} / {@code complete}
 * 的 {@code claim_id} 条件）：否则租约过期后原节点仍能写，等于把新主人刚立的占位抹掉，
 * 第三个节点就会再领一次同一行。</p>
 *
 * <p>⭐ <b>{@code inFlightCodes} 参数为什么存在</b>：{@code status} 存的是整型码，
 * 若 SQL 里直接写 {@code status in (0, 1)}，下一个往枚举里加在途态的人不会想到改 XML，
 * 症状是「新状态的任务永远扫不到」——静默、且要等积压才显形。
 * 由调用方（gateway 侧 {@code AsyncTaskStatusEnum#inFlightCodes()}）传参，加常量即自动生效。
 * ⚠ dal 不认识状态语义，也不该认识：它只负责把数字塞进谓词。</p>
 */
public interface AsyncTaskMapper {

    AsyncTaskEntity getById(@Param("id") Long id);

    AsyncTaskEntity getByTaskCode(@Param("taskCode") String taskCode);

    List<AsyncTaskEntity> getByClaimId(@Param("claimId") String claimId);

    int insert(AsyncTaskEntity entity);

    /**
     * 原子抢占一批到期任务：只抢「未占位或占位已过期」的行。
     *
     * @param claimId        本次抢占标识
     * @param claimExpires   占位过期时刻
     * @param limit          单批上限
     * @param inFlightCodes  在途状态码集合（见类注释）
     * @return 抢到的行数
     */
    int claim(@Param("claimId") String claimId,
              @Param("claimExpires") LocalDateTime claimExpires,
              @Param("limit") int limit,
              @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 抢占一批已超时、仍在途的任务：与 {@link #claim} 是互补的两个入口
     * （一个只挑 {@code expires_at} 未到的行，一个只挑已到的行），
     * 抢到之后才允许按 {@code TIMEOUT} 落终态。
     *
     * <p>刻意不提供「批量改状态」的单条 UPDATE：那样会绕过状态机与终态事件发布，
     * 超时任务就会在数仓里彻底不可见。</p>
     *
     * @param claimId        本次抢占标识
     * @param claimExpires   占位过期时刻
     * @param limit          单批上限
     * @param inFlightCodes  在途状态码集合
     * @return 抢到的行数
     */
    int claimExpired(@Param("claimId") String claimId,
                     @Param("claimExpires") LocalDateTime claimExpires,
                     @Param("limit") int limit,
                     @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 释放占位并安排下一次轮询（非终态继续）。
     *
     * @param claimId        必须与行上一致才写；不一致说明占位已易主（本节点处理超时），
     *                       此时不写，避免把新主人的占位抹掉
     * @param nextStatus     写入的状态码（通常是在途态里的 POLLING）
     * @param inFlightCodes  CAS 守卫用的在途状态码集合
     */
    int scheduleNext(@Param("id") Long id,
                     @Param("claimId") String claimId,
                     @Param("nextPollAt") LocalDateTime nextPollAt,
                     @Param("lastPolledAt") LocalDateTime lastPolledAt,
                     @Param("nextStatus") int nextStatus,
                     @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 终态落定：CAS —— 只有仍处于在途态<b>且仍持有占位</b>的行才被更新，
     * 影响行数为 1 才允许发终态事件，「一条任务一条终态消息」由此保证。
     */
    int complete(@Param("id") Long id,
                 @Param("claimId") String claimId,
                 @Param("status") int status,
                 @Param("resultJson") String resultJson,
                 @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage,
                 @Param("completedAt") LocalDateTime completedAt,
                 @Param("inFlightCodes") List<Integer> inFlightCodes);

    /** 清理已归档窗口之前的终态行（终态 = 不在 {@code inFlightCodes} 里） */
    int deleteCompletedBefore(@Param("before") LocalDateTime before,
                              @Param("limit") int limit,
                              @Param("inFlightCodes") List<Integer> inFlightCodes);
}
