package org.chobit.knot.svc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.constants.enums.AsyncTaskDeliveryStatusEnum;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.dal.mapper.AsyncTaskMapper;
import org.chobit.knot.svc.converter.AsyncTaskConverter;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.dto.async.AsyncTaskQuery;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步任务实例（{@code kb_async_tasks}）的读写。
 *
 * <p><b>DB 是唯一真相源</b>：不以 Redis / 进程内存为主存储。缓存不可用不应放大成
 * 「上游异步任务永久丢失、永远不计费」——那条路的终点是资损且无从察觉。
 * 代价是每次 create 多一次 INSERT、每次轮询多一次 UPDATE，对视频这类分钟级任务完全可以接受。</p>
 *
 * <p>⚠ 与同包的 {@link AsyncTaskProfileService} 分成两个服务，理由不是「两张表」，
 * 而是<b>两种生命周期与两种权限</b>：档案（查询口径）由管理面维护、读多写少、几乎不变，
 * 未来的增删改会长在那个服务上；任务实例是运行时的热数据，只有网关在写。
 * 合成一个类会让「谁能改档案」这个问题失去边界——而这条边界恰恰是要靠类型表达的。</p>
 *
 * <h3>⭐ 每次写都必须带 {@code claimId}</h3>
 * <p>它不是「顺手带上的条件」，而是<b>租约的兑付口</b>。去掉它之后，租约过期的原节点仍能写，
 * 等于把新主人刚立的占位抹掉，第三个节点会再领同一行。</p>
 *
 * <p>本类与 {@link AsyncTaskProfileService} 是模块内仅有的碰异步任务 Mapper 的类
 * （另加 {@link AsyncTaskConverter} 碰实体）；编排在 {@code knot-gateway}，
 * 它只经本类间接用库，不 import 任何 {@code org.chobit.knot.dal} 类型。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskService {

    private final AsyncTaskMapper mapper;

    /** 落一条新任务（在途，等待首次轮询） */
    public void insert(AsyncTaskDraftDto draft) {
        mapper.insert(AsyncTaskConverter.toEntity(draft));
    }

    public AsyncTaskDto getByTaskCode(String taskCode) {
        return AsyncTaskConverter.toRecord(mapper.getByTaskCode(taskCode));
    }

    /**
     * 管理端分页检索（{@code PageHelper} 由调用方开启；分页是展示关切，不进 svc）。
     *
     * <p>⚠ 返回的记录里四个 JSON 段全是 {@code null}——列表 SQL 刻意没取它们（见
     * {@code AsyncTaskMapper#search}）。要 JSON 就走 {@link #getByTaskCode}。</p>
     */
    public List<AsyncTaskDto> search(AsyncTaskQuery query) {
        return mapper.search(
                        query == null ? null : blankToNull(query.keyword()),
                        query == null ? null : blankToNull(query.profileCode()),
                        query == null ? null : blankToNull(query.protocol()),
                        query == null ? null : query.status(),
                        query == null ? null : query.submittedFrom(),
                        query == null ? null : query.submittedTo())
                .stream()
                .map(AsyncTaskConverter::toRecord)
                .toList();
    }

    /**
     * 按主键抢占一行（管理端「手动终止」的第一步）。
     *
     * <p>⭐ 抢占是<b>写终态的前置条件</b>，不是可选的礼貌动作：{@code complete} 以
     * {@code claim_id} 为兑付口，不持租约去写，要么写不进，要么把别人的占位抹掉。</p>
     *
     * <p>抢到之后<b>必须重读</b>：返回的行带的是本次的 {@code claim_id}，后续 {@code complete}
     * 要靠它兑现租约。抢不到说明这行现在不归调用方处置（已终态，或被别的节点有效占位）。</p>
     *
     * @return 抢到的那一行（含本次占位号）；抢不到返回 {@code null}
     */
    public AsyncTaskDto claimById(Long id, String claimId, Duration claimTtl) {
        requireClaimId(claimId);
        boolean claimed = mapper.claimById(id, claimId, LocalDateTime.now().plus(claimTtl),
                AsyncTaskStatusEnum.inFlightCodes()) > 0;
        return claimed ? AsyncTaskConverter.toRecord(mapper.getById(id)) : null;
    }

    /**
     * 重新打开一条可重试的终态任务（{@code FAILED} / {@code EXPIRED} → {@code PENDING}）。
     *
     * <p>⚠ <b>{@code expiresAt} 必须由调用方算好传入</b>：它来自档案的 {@code timeout_ms}，
     * 而 dal 与 svc 都不解析档案。传原值（提交时刻 + 超时）会让重开的行在下一拍就被
     * {@code claimExpired} 收掉——表现为「点了重试什么都没发生」。</p>
     *
     * <p>⭐ 交付维度（{@code delivery_status/attempts/next_delivery_at/...}）一并归零，
     * {@code callback_url} 保留：不复位的话，停在待回调的行会先按已清空的 {@code result_json}
     * 回调一次，新终态到达后再武装一次——同一个任务两次回调。</p>
     *
     * @return {@code false} 表示状态已经变了（另一个运维刚重试过、或任务已被推进），不是错误
     */
    public boolean reopen(Long id, LocalDateTime nextPollAt, LocalDateTime expiresAt) {
        return mapper.reopen(id, AsyncTaskStatusEnum.PENDING.code(), nextPollAt, expiresAt,
                AsyncTaskStatusEnum.retryableCodes(), AsyncTaskDeliveryStatusEnum.NONE.code()) > 0;
    }

    /**
     * 抢占一批到期任务：返回被本调用抢到的行（空列表表示本轮无到期任务）。
     *
     * <p>多节点同时 tick 时靠 DB 行锁互斥：一条 {@code UPDATE ... ORDER BY ... LIMIT} 完成抢占，
     * 再按占位号回查。节点宕机不释放占位，靠 {@code claim_expires_at} 到期自动回收——
     * 于是优雅停机不需要任何额外钩子。</p>
     */
    public List<AsyncTaskDto> claim(String claimId, Duration claimTtl, int limit) {
        LocalDateTime expires = LocalDateTime.now().plus(claimTtl);
        int claimed = mapper.claim(claimId, expires, limit, AsyncTaskStatusEnum.inFlightCodes());
        if (claimed <= 0) {
            return List.of();
        }
        return claimed(claimId);
    }

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
    public List<AsyncTaskDto> claimExpired(String claimId, Duration claimTtl, int limit) {
        LocalDateTime expires = LocalDateTime.now().plus(claimTtl);
        int claimed = mapper.claimExpired(claimId, expires, limit, AsyncTaskStatusEnum.inFlightCodes());
        if (claimed <= 0) {
            return List.of();
        }
        return claimed(claimId);
    }

    /**
     * 非终态：安排下一次轮询并释放占位。
     *
     * @param claimId 本次处理持有的占位号；<b>必须与行上的一致</b>，否则不写
     * @return 是否写入成功；{@code false} 表示占位已易主（本节点处理超时），调用方不得再当自己是主人
     */
    public boolean scheduleNext(Long id, String claimId, LocalDateTime nextPollAt) {
        requireClaimId(claimId);
        return mapper.scheduleNext(id, claimId, nextPollAt, LocalDateTime.now(),
                AsyncTaskStatusEnum.POLLING.code(), AsyncTaskStatusEnum.inFlightCodes()) > 0;
    }

    /**
     * 落终态：CAS，只有在途态<b>且仍持有占位</b>才更新。
     *
     * <p>⭐ 同一条 SQL 顺手<b>武装回调交付</b>（{@code delivery_status → 待回调}、
     * {@code next_delivery_at → 此刻}）。放进终态 CAS 而不是「落终态后再写一次」：
     * 中间崩溃会留下「任务已终态、却永远不会回调」的行，而那一行没有任何报错。</p>
     *
     * @param claimId            本次处理持有的占位号
     * @param status             终态码（{@link AsyncTaskStatusEnum#code()}）；非终态不得传
     * @param deliveryPendingCode 待回调状态码（{@link AsyncTaskDeliveryStatusEnum#PENDING}），
     *                           仅当行上有 {@code callback_url} 时生效
     * @return 是否由本调用落定；false 表示已被别的节点落过（或占位已易主），
     * 调用方不得再发终态事件——终态事件的口径是「一条任务一条」，由真正落定的人发
     */
    public boolean complete(Long id, String claimId, int status, String resultJson,
                            String errorCode, String errorMessage, int deliveryPendingCode) {
        requireClaimId(claimId);
        return mapper.complete(id, claimId, status, resultJson, errorCode, errorMessage,
                LocalDateTime.now(), deliveryPendingCode, AsyncTaskStatusEnum.inFlightCodes()) > 0;
    }

    // ======================= 回调交付（与轮询正交） =======================

    /**
     * 抢占一批到期待投递任务：返回被本调用抢到的行。
     *
     * <p>与 {@link #claim} 是两条独立租约（{@code delivery_claim_id}），理由见 mapper 注释。</p>
     */
    public List<AsyncTaskDto> claimDeliveries(String claimId, Duration claimTtl, int limit) {
        LocalDateTime expires = LocalDateTime.now().plus(claimTtl);
        int claimed = mapper.claimDeliveries(claimId, expires, limit,
                AsyncTaskDeliveryStatusEnum.deliverableCodes());
        if (claimed <= 0) {
            return List.of();
        }
        return mapper.getByDeliveryClaimId(claimId).stream()
                .map(AsyncTaskConverter::toRecord)
                .toList();
    }

    /**
     * 交付落定（成功 / 最终放弃）：CAS，仍待投递且仍持有交付占位才写。
     *
     * @return {@code false} 表示交付占位已易主——本次投递的结果不归本节点报
     */
    public boolean completeDelivery(Long id, String claimId, int status, int attempts,
                                    LocalDateTime deliveredAt, String error) {
        requireClaimId(claimId);
        return mapper.completeDelivery(id, claimId, status, attempts, deliveredAt, error,
                AsyncTaskDeliveryStatusEnum.deliverableCodes()) > 0;
    }

    /**
     * 交付失败但未到上限：退避后重试（释放交付占位）。
     *
     * @return {@code false} 表示交付占位已易主
     */
    public boolean rescheduleDelivery(Long id, String claimId, int attempts,
                                      LocalDateTime nextDeliveryAt, String error) {
        requireClaimId(claimId);
        return mapper.rescheduleDelivery(id, claimId, attempts, nextDeliveryAt, error,
                AsyncTaskDeliveryStatusEnum.deliverableCodes()) > 0;
    }

    /**
     * 清理已过保留窗口的终态行。
     *
     * <p>⚠ 除了「终态 + 过了保留窗口」，还要排掉<b>仍待回调</b>的行：它已终态但仍欠客户端一次
     * 结果投递，删掉等于把结果连同 {@code result_json} 一起静默丢弃。判据在交付维度，不在状态。</p>
     */
    public int cleanup(LocalDateTime before, int limit) {
        return mapper.deleteCompletedBefore(before, limit,
                AsyncTaskStatusEnum.inFlightCodes(), AsyncTaskDeliveryStatusEnum.deliverableCodes());
    }

    /**
     * 占位号为空说明调用方没走抢占就写库——这类错误必须当场炸掉。
     *
     * <p>⚠ 不能让它落到 SQL 里：{@code claim_id = null} 在 SQL 里恒为 unknown，
     * 影响行数永远是 0，表现成「任务一直不落终态、也不报错」，
     * 排查时要一路追到 mapper 才看得出，比直接抛异常贵得多。</p>
     */
    private static void requireClaimId(String claimId) {
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("async task write requires a claim id");
        }
    }

    private List<AsyncTaskDto> claimed(String claimId) {
        return mapper.getByClaimId(claimId).stream()
                .map(AsyncTaskConverter::toRecord)
                .toList();
    }

    /**
     * 空白归一成 {@code null}。
     *
     * <p>⚠ 与 mapper 里的 {@code keyword != ''} 是<b>两层但同一个口径</b>：这里把
     * {@code "  "}（全空格）折成 null，XML 里处理空串。少任何一层都会出现
     * 「输入一个空格 → 结果为空」这种肉眼找不到原因的查询。</p>
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
