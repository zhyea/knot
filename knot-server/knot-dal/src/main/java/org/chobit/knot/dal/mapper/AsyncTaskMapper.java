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
                 @Param("deliveryPendingCode") int deliveryPendingCode,
                 @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 清理已归档窗口之前的终态行（终态 = 不在 {@code inFlightCodes} 里）。
     *
     * <p>⭐ <b>还要排掉「仍待投递」的行</b>：这类行已经终态、过了保留窗口也该清，但它还欠客户端
     * 一次回调投递——只按 {@code status} 删，会把结果连同 {@code result_json} 一起静默丢弃，
     * 客户端既收不到回调也查不到轮询，且没有任何报错。判据落在交付维度，与 {@code status} 无关。</p>
     *
     * @param deliverableCodes 待投递状态码集合（由 {@code AsyncTaskDeliveryStatusEnum#deliverableCodes()} 传入）
     */
    int deleteCompletedBefore(@Param("before") LocalDateTime before,
                              @Param("limit") int limit,
                              @Param("inFlightCodes") List<Integer> inFlightCodes,
                              @Param("deliverableCodes") List<Integer> deliverableCodes);

    /**
     * 管理端分页检索（{@code PageHelper} 在调用方开启分页）。
     *
     * <p>⭐ <b>刻意只取摘要列</b>：四个 JSON 段（{@code request_json} / {@code poll_spec_json} /
     * {@code context_json} / {@code result_json}）单行可达数十 KB，一页 20 行就是 MB 级传输，
     * 而列表页一个都不展示。未取到的字段在实体里是 {@code null}，
     * <b>不是「数据为空」</b>——要看它们必须走 {@link #getByTaskCode}（全列）。
     * 这条差别写在这里，因为「列表接口返回的 requestJson 居然是 null」是最容易被误判成 bug 的现象。</p>
     *
     * @param keyword       任务句柄 / 上游任务号 的模糊匹配；空则不过滤
     * @param profileCode   档案编码；空则不过滤
     * @param protocol      API 协议编码；空则不过滤
     * @param status        状态码；{@code null} 表示全部（⚠ 这里只支持单值筛选，
     *                      「在途」这种分组筛选由调用方传两个码中的任意一个，不做 IN 展开）
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
     * 按主键抢占一行（管理端「手动终止」用）。
     *
     * <p>⭐ 与 {@link #claim} 的差别只有两处：<b>按 id 定位</b>、<b>不要求 {@code next_poll_at} 到期</b>。
     * 为什么仍然要抢占而不是直接改状态：写库（{@code complete}）以 {@code claim_id} 为兑付口，
     * 不持租约就写——要么写不进（行被轮询节点持有），要么写进去意味着把别人的占位抹掉。
     * 抢占失败（影响行数 0）就是「这一行现在不归你处置」，调用方据此回冲突，而不是重试到成功。</p>
     *
     * <p>不写 {@code last_polled_at}：这一轮没有真的查上游。</p>
     */
    int claimById(@Param("id") Long id,
                  @Param("claimId") String claimId,
                  @Param("claimExpires") LocalDateTime claimExpires,
                  @Param("inFlightCodes") List<Integer> inFlightCodes);

    /**
     * 重新打开一条终态任务（管理端「重试」）：{@code FAILED} / {@code EXPIRED} → 在途，轮次 +1。
     *
     * <p>⭐ <b>为什么重置 {@code attempts} 与 {@code completed_at}</b>：{@code attempts} 是
     * 「本轮查了几次」，退避与 {@code max_attempts} 都按它算，不归零则重试的第一次查询就会
     * 直接判次数耗尽；{@code completed_at} 留在行上会让「这条任务还终态着」与
     * 「{@code next_poll_at} 有值」两个事实同时成立，排查时自相矛盾。</p>
     *
     * <p>⭐ <b>{@code expires_at} 必须由调用方重算</b>：原值来自提交时刻 + 档案超时，
     * 一个三天前超时的任务重开后 {@code expires_at} 早已过去，下一拍会被
     * {@code claimExpired} 立刻按超时收掉——重试表现为「什么都没发生」。dal 不读档案，
     * 这个数只能由调用方算好传进来。</p>
     *
     * <p>⭐ {@code retry_round + 1} 不是装饰：它是重试后事件流的去重坐标，见
     * {@code AsyncTaskEntity#retryRound} 注释。</p>
     *
     * <p>⭐ <b>交付维度一并复位成「无需回调」</b>{@code callbackUrl} 保留（任务固有属性）。
     * 只清 {@code status/result_json} 而留着 {@code delivery_status=PENDING} 的后果是：
     * 仍在退避中的交付节拍会拿着已清空的结果先回调一次，新终态到达后再武装一次——
     * 同一个任务两次回调，且第一次推的是空结果。这里不必判断「该不该置成待回调」：
     * 重开后在途，等新终态落库时 {@link #complete} 会在同一条 CAS 里原子武装。</p>
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
     * 原子抢占一批到期待投递任务。
     *
     * <p>⭐ 只挑「待回调」状态（正向包含），与轮询的 {@link #claim} 是两条独立租约：
     * 交付发生在终态之后，轮询租约此时已释放，复用会让两个「由谁负责」的事实混在一个字段里。</p>
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
