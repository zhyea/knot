package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.AsyncTaskEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步任务实例读写。
 * {@code claim} 借 {@code UPDATE ... ORDER BY ... LIMIT} 的行锁完成抢占，再按 {@code claimId}
 * 回查自己抢到的行；每次写都必须带 {@code claim_id} 条件，否则租约过期后仍能写。
 * {@code inFlightCodes} 由调用方传入在途状态码集合，dal 不认识状态语义。
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
     * 抢占一批已超时、仍在途的任务：与 {@link #claim} 互补（一个只挑 {@code expires_at}
     * 未到的行，一个只挑已到的行），抢到之后才允许按 {@code TIMEOUT} 落终态。
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
     * 把一行标记为「开始轮询」：{@code PENDING} 的语义是「提交后一次查询都没发起过」，
     * 所以在发起查询之前就要写这一条。受租约保护（{@code claim_id} 条件）。
     *
     * @param pollingCode   轮询中状态码（由 {@code AsyncTaskStatusEnum#POLLING} 传入，dal 不认识语义）
     * @param inFlightCodes CAS 守卫用的在途状态码集合
     * @return 影响行数；0 表示占位已易主或行已终态（此时不该再调上游）
     */
    int markPolling(@Param("id") Long id,
                    @Param("claimId") String claimId,
                    @Param("pollingCode") int pollingCode,
                    @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 释放占位并安排下一次轮询（非终态继续）。
     *
     * @param claimId        必须与行上一致才写；不一致说明占位已易主（本节点处理超时），
     *                       此时不写，避免把新主人的占位抹掉
     * @param nextStatus     写入的状态码（在途态里的 POLLING）；{@link #markPolling} 已经写过一次，
     *                       这里再写一遍是兜底——漏标时非终态这条路径仍能把行推进到「轮询中」
     * @param inFlightCodes  CAS 守卫用的在途状态码集合
     */
    int scheduleNext(@Param("id") Long id,
                     @Param("claimId") String claimId,
                     @Param("nextPollAt") LocalDateTime nextPollAt,
                     @Param("lastPolledAt") LocalDateTime lastPolledAt,
                     @Param("nextStatus") int nextStatus,
                     @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 终态落定：CAS —— 只有仍处于在途态且仍持有占位的行才被更新，
     * 影响行数为 1 才允许发终态事件，「一条任务一条终态消息」由此保证。
     * {@code terminalBody} / {@code settlementJson} 与终态状态走同一条 UPDATE。
     *
     * @param terminalBody   上游终态查询响应原文；无响应（超时/快照不可读）传 {@code null}
     * @param settlementJson 结算快照（{@code knot_extend} 同形）；不计费路径传 {@code null}
     */
    int complete(@Param("id") Long id,
                 @Param("claimId") String claimId,
                 @Param("status") int status,
                 @Param("resultJson") String resultJson,
                 @Param("terminalBody") String terminalBody,
                 @Param("settlementJson") String settlementJson,
                 @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage,
                 @Param("completedAt") LocalDateTime completedAt,
                 @Param("deliveryPendingCode") int deliveryPendingCode,
                 @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 清理已归档窗口之前的终态行（终态 = 不在 {@code inFlightCodes} 里），
     * 并排掉仍待投递的行：这类行已终态，但还欠客户端一次回调。
     *
     * @param deliverableCodes 待投递状态码集合（由 {@code AsyncTaskDeliveryStatusEnum#deliverableCodes()} 传入）
     */
    int deleteCompletedBefore(@Param("before") LocalDateTime before,
                              @Param("limit") int limit,
                              @Param("inFlightCodes") List<Integer> inFlightCodes,
                              @Param("deliverableCodes") List<Integer> deliverableCodes);

    /**
     * 管理端分页检索（{@code PageHelper} 在调用方开启分页）。只取摘要列：四个 JSON 段
     * （{@code request_json} / {@code poll_spec_json} / {@code context_json} / {@code result_json}）
     * 在实体里为 {@code null}，不是「数据为空」；要看它们必须走 {@link #getByTaskCode}（全列）。
     *
     * @param keyword       任务句柄 / 上游任务号 的模糊匹配；空则不过滤
     * @param profileCode   档案编码；空则不过滤
     * @param protocol      API 协议编码；空则不过滤
     * @param status        状态码；{@code null} 表示全部（只支持单值筛选，「在途」由调用方传两个码中的任意一个，不做 IN 展开）
     * @param submittedFrom 提交时间下界（含）；{@code null} 不限
     * @param submittedTo   提交时间上界（含）；{@code null} 不限
     */
    List<AsyncTaskEntity> search(@Param("keyword") String keyword,
                                 @Param("profileCode") String profileCode,
                                 @Param("protocol") String protocol,
                                 @Param("status") Integer status,
                                 @Param("submittedFrom") LocalDateTime submittedFrom,
                                 @Param("submittedTo") LocalDateTime submittedTo);

    /**
     * 按主键抢占一行（管理端「手动终止」用）：与 {@link #claim} 的差别是按 id 定位、
     * 不要求 {@code next_poll_at} 到期；抢占失败（影响行数 0）表示该行当前不归本节点处置。
     * 不写 {@code last_polled_at}：这一轮没有真的查上游。
     */
    int claimById(@Param("id") Long id,
                  @Param("claimId") String claimId,
                  @Param("claimExpires") LocalDateTime claimExpires,
                  @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 重新打开一条终态任务（管理端「重试」）：{@code FAILED} / {@code EXPIRED} → 在途，
     * 轮次 +1，{@code attempts} 与 {@code completed_at} 一并重置。
     * {@code expires_at} 必须由调用方重算后传入（dal 不读档案）；交付维度复位成「无需回调」，
     * {@code callback_url} 保留，新终态落库时由 {@link #complete} 在同一条 CAS 里原子武装。
     *
     * @param retryableCodes 允许被重开的状态码集合（由 {@code AsyncTaskStatusEnum#retryableCodes()} 传入）
     * @param deliveryNoneCode 交付维度的「无需回调」码（由 {@code AsyncTaskDeliveryStatusEnum#NONE} 传入）
     * @return 影响行数；0 表示状态已变（比如另一个运维刚重试过、或任务已被轮询推进）
     */
    int reopen(@Param("id") Long id,
               @Param("nextStatus") int nextStatus,
               @Param("nextPollAt") LocalDateTime nextPollAt,
               @Param("expiresAt") LocalDateTime expiresAt,
               @Param("retryableCodes") List<Integer> retryableCodes,
               @Param("deliveryNoneCode") int deliveryNoneCode);

    // ======================= 回调交付（与轮询正交） =======================

    /** 按交付占位号回查被本次抢占的行（与 {@link #getByClaimId} 同形态） */
    List<AsyncTaskEntity> getByDeliveryClaimId(@Param("claimId") String claimId);

    /**
     * 原子抢占一批到期待投递任务：只挑「待回调」状态（正向包含），
     * 与轮询的 {@link #claim} 是两条独立租约。
     *
     * @param claimId          本次交付抢占标识
     * @param claimExpires     交付占位过期时刻
     * @param limit            单批上限
     * @param deliverableCodes 待投递状态码集合（见 {@code AsyncTaskDeliveryStatusEnum#deliverableCodes()}）
     * @return 抢到的行数
     */
    int claimDeliveries(@Param("claimId") String claimId,
                        @Param("claimExpires") LocalDateTime claimExpires,
                        @Param("limit") int limit,
                        @Param("deliverableCodes") List<Integer> deliverableCodes);

    /**
     * 交付落定（成功 / 最终放弃）：CAS —— 仍待投递且仍持有交付占位才更新。
     *
     * @param status     交付结果码（{@code AsyncTaskDeliveryStatusEnum} 的 succeeded / failed）
     * @param attempts   含本次在内的总尝试次数
     * @param deliveredAt 成功送达时刻；放弃时为 {@code null}
     * @param error      失败原因摘要；成功时为 {@code null}
     */
    int completeDelivery(@Param("id") Long id,
                         @Param("claimId") String claimId,
                         @Param("status") int status,
                         @Param("attempts") int attempts,
                         @Param("deliveredAt") LocalDateTime deliveredAt,
                         @Param("error") String error,
                         @Param("deliverableCodes") List<Integer> deliverableCodes);

    /**
     * 交付失败但未到上限：退避后重试（释放交付占位）。
     *
     * @param attempts       含本次在内的总尝试次数
     * @param nextDeliveryAt 下一次投递时刻
     * @param error          本次失败原因摘要
     */
    int rescheduleDelivery(@Param("id") Long id,
                           @Param("claimId") String claimId,
                           @Param("attempts") int attempts,
                           @Param("nextDeliveryAt") LocalDateTime nextDeliveryAt,
                           @Param("error") String error,
                           @Param("deliverableCodes") List<Integer> deliverableCodes);
}
