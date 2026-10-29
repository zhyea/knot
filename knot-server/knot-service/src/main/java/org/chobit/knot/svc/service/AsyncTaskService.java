package org.chobit.knot.svc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.dal.mapper.AsyncTaskMapper;
import org.chobit.knot.svc.converter.AsyncTaskConverter;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
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
     * @param claimId 本次处理持有的占位号
     * @param status  终态码（{@link AsyncTaskStatusEnum#code()}）；非终态不得传
     * @return 是否由本调用落定；false 表示已被别的节点落过（或占位已易主），
     * 调用方不得再发终态事件——终态事件的口径是「一条任务一条」，由真正落定的人发
     */
    public boolean complete(Long id, String claimId, int status, String resultJson,
                            String errorCode, String errorMessage) {
        requireClaimId(claimId);
        return mapper.complete(id, claimId, status, resultJson, errorCode, errorMessage,
                LocalDateTime.now(), AsyncTaskStatusEnum.inFlightCodes()) > 0;
    }

    /** 清理已过保留窗口的终态行 */
    public int cleanup(LocalDateTime before, int limit) {
        return mapper.deleteCompletedBefore(before, limit, AsyncTaskStatusEnum.inFlightCodes());
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
}
