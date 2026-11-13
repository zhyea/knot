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
import org.chobit.knot.svc.dto.async.AsyncTaskTerminalDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步任务实例（{@code kb_async_tasks}）的读写；DB 是唯一真相源，不以 Redis / 进程内存为主存储。
 * 每次写都必须带 {@code claimId}——它是租约的兑付口。
 * 模块内仅本类与 {@link AsyncTaskProfileService} 碰异步任务 Mapper（{@link AsyncTaskConverter} 碰实体），
 * 编排在 {@code knot-gateway}，只经本类间接用库。
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
     * 管理端分页检索（分页由调用方用 {@code PageHelper} 开启）。
     * 返回记录的四个 JSON 段全为 {@code null}，需要 JSON 走 {@link #getByTaskCode}。
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
     * 按主键抢占一行（管理端「手动终止」的第一步）：抢占是写终态的前置条件。
     * 抢到后必须重读，后续 {@code complete} 用返回行上的 {@code claim_id} 兑现租约。
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
     * {@code expiresAt} 必须由调用方按档案的 {@code timeout_minutes} 算好传入；
     * 交付维度（{@code delivery_status/attempts/next_delivery_at/...}）一并归零，{@code callback_url} 保留。
     *
     * @return {@code false} 表示状态已经变了（另一个运维刚重试过、或任务已被推进），不是错误
     */
    public boolean reopen(Long id, LocalDateTime nextPollAt, LocalDateTime expiresAt) {
        return mapper.reopen(id, AsyncTaskStatusEnum.PENDING.code(), nextPollAt, expiresAt,
                AsyncTaskStatusEnum.retryableCodes(), AsyncTaskDeliveryStatusEnum.NONE.code()) > 0;
    }

    /**
     * 抢占一批到期任务，返回被本调用抢到的行；空列表表示本轮无到期任务。
     * 多节点同时 tick 靠 DB 行锁互斥；节点宕机不释放占位，靠 {@code claim_expires_at} 到期回收。
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
     * 抢占一批已过 knot 侧超时的在途任务，交给上层按 {@code TIMEOUT} 落终态。
     * 与 {@link #claim} 互斥：{@code claim} 只挑 {@code expires_at} 未到的行，本方法只挑已到期的行。
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
     * 发起查询之前把行推进到「轮询中」（{@code PENDING → POLLING}）。
     * 与其它写一样带 {@code claimId}：租约易主时返回 {@code false}。
     *
     * @return 是否写入成功；{@code false} 表示占位已易主或行已终态
     */
    public boolean markPolling(Long id, String claimId) {
        requireClaimId(claimId);
        return mapper.markPolling(id, claimId, AsyncTaskStatusEnum.POLLING.code(),
                AsyncTaskStatusEnum.inFlightCodes()) > 0;
    }

    /**
     * 非终态：安排下一次轮询并释放占位。
     * @param claimId 本次处理持有的占位号；必须与行上的一致，否则不写
     * @return 是否写入成功；{@code false} 表示占位已易主（本节点处理超时），调用方不得再当自己是主人
     */
    public boolean scheduleNext(Long id, String claimId, LocalDateTime nextPollAt) {
        requireClaimId(claimId);
        return mapper.scheduleNext(id, claimId, nextPollAt, LocalDateTime.now(),
                AsyncTaskStatusEnum.POLLING.code(), AsyncTaskStatusEnum.inFlightCodes()) > 0;
    }

    /**
     * 落终态：CAS，只有在途态且仍持有占位才更新；同一条 SQL 顺手武装回调交付
     * （{@code delivery_status → 待回调}、{@code next_delivery_at → 此刻}）。
     *
     * @param claimId            本次处理持有的占位号
     * @param status             终态码（{@link AsyncTaskStatusEnum#code()}）；非终态不得传
     * @param terminal           终态证据（上游原文 + 结算快照）；无证据传 {@link AsyncTaskTerminalDto#EMPTY}
     * @param deliveryPendingCode 待回调状态码（{@link AsyncTaskDeliveryStatusEnum#PENDING}），
     *                           仅当行上有 {@code callback_url} 时生效
     * @return 是否由本调用落定；false 表示已被别的节点落过（或占位已易主），
     * 调用方不得再发终态事件——终态事件的口径是「一条任务一条」，由真正落定的人发
     */
    public boolean complete(Long id, String claimId, int status, String resultJson,
                            AsyncTaskTerminalDto terminal,
                            String errorCode, String errorMessage, int deliveryPendingCode) {
        requireClaimId(claimId);
        AsyncTaskTerminalDto evidence = terminal == null ? AsyncTaskTerminalDto.EMPTY : terminal;
        return mapper.complete(id, claimId, status, resultJson,
                evidence.terminalBody(), evidence.settlementJson(),
                errorCode, errorMessage,
                LocalDateTime.now(), deliveryPendingCode, AsyncTaskStatusEnum.inFlightCodes()) > 0;
    }

    // ======================= 回调交付（与轮询正交） =======================

    /**
     * 抢占一批到期待投递任务，返回被本调用抢到的行；与 {@link #claim} 是两条独立租约
     * （{@code delivery_claim_id}）。
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
     * 清理已过保留窗口的终态行；仍待回调（欠客户端一次结果投递）的行不清理。
     */
    public int cleanup(LocalDateTime before, int limit) {
        return mapper.deleteCompletedBefore(before, limit,
                AsyncTaskStatusEnum.inFlightCodes(), AsyncTaskDeliveryStatusEnum.deliverableCodes());
    }

    /**
     * 占位号为空即抛异常：说明调用方没走抢占就写库。
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
     * 空白（全空格）归一成 {@code null}；空串由 mapper 的 {@code keyword != ''} 处理。
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
