package org.chobit.knot.gateway.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.dal.mapper.AsyncTaskMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link AsyncTaskStore} 的 DB 实现：本轮唯一实现。
 *
 * <p>所有写操作都直接落到 {@code kb_async_tasks}；轮询的「待办集合」就是这张表本身，
 * 因此不需要额外的队列或缓存层 —— 少一份状态就少一处漂移。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbAsyncTaskStore implements AsyncTaskStore {

    private final AsyncTaskMapper mapper;

    @Override
    public void insert(AsyncTaskEntity task) {
        mapper.insert(task);
    }

    @Override
    public AsyncTaskEntity getByTaskCode(String taskCode) {
        return mapper.getByTaskCode(taskCode);
    }

    @Override
    public List<AsyncTaskEntity> claim(String claimId, Duration claimTtl, int limit) {
        LocalDateTime expires = LocalDateTime.now().plus(claimTtl);
        int claimed = mapper.claim(claimId, expires, limit);
        if (claimed <= 0) {
            return List.of();
        }
        return mapper.getByClaimId(claimId);
    }

    @Override
    public List<AsyncTaskEntity> claimExpired(String claimId, Duration claimTtl, int limit) {
        LocalDateTime expires = LocalDateTime.now().plus(claimTtl);
        int claimed = mapper.claimExpired(claimId, expires, limit);
        if (claimed <= 0) {
            return List.of();
        }
        return mapper.getByClaimId(claimId);
    }

    @Override
    public boolean scheduleNext(Long id, String claimId, LocalDateTime nextPollAt) {
        requireClaimId(claimId);
        return mapper.scheduleNext(id, claimId, nextPollAt, LocalDateTime.now()) > 0;
    }

    @Override
    public boolean complete(Long id, String claimId, String status, String resultJson,
                            String errorCode, String errorMessage) {
        requireClaimId(claimId);
        return mapper.complete(id, claimId, status, resultJson, errorCode, errorMessage,
                LocalDateTime.now()) > 0;
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

    @Override
    public int cleanup(LocalDateTime before, int limit) {
        return mapper.deleteCompletedBefore(before, limit);
    }
}
