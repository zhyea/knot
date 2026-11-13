package org.chobit.knot.gateway.async.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.async.AsyncTaskEvent;
import org.chobit.knot.gateway.async.AsyncTaskProperties;
import org.chobit.knot.gateway.async.AsyncTaskRunner;
import org.chobit.knot.gateway.async.AsyncTaskSpec;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.dto.async.AsyncTaskOperationResponse;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 异步任务的运维动作（终止 / 重试）：拿租约、判可执行性、把动作交给编排层。
 * 抢占失败如实回报「任务正被轮询节点处理」，不重试到成功。
 * 两个动作都先读当前行再判断，返回「已终态 / 被占用 / 状态不合法」等原因，不靠写失败推断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskAdminRunner {

    /** 占位号里区分「运维动作」与「轮询」：事后按 {@code claim_id} 就能看出这行是谁动的 */
    private static final String CLAIM_MARKER = "-admin-";

    private final AsyncTaskService taskService;
    private final AsyncTaskRunner runner;
    private final AsyncTaskProperties properties;

    /**
     * 终止一条在途任务（落 {@code CANCELLED}，不计费，并发一条终态事件）。
     *
     * @return 动作后的任务状态；任务不存在返回 {@code null}（由 controller 映射成 404）
     */
    public AsyncTaskOperationResponse cancel(String taskCode) {
        AsyncTaskDto current = taskService.getByTaskCode(normalize(taskCode));
        if (current == null) {
            return null;
        }
        AsyncTaskStatusEnum status = statusOf(current);
        if (status == null) {
            return missed(current, "任务状态码无法识别，请人工核对这一行");
        }
        if (status.terminal()) {
            return missed(current, "任务已是终态（" + status.label() + "），无需终止");
        }
        AsyncTaskDto claimed = taskService.claimById(current.id(), newClaimId(),
                Duration.ofMillis(properties.getClaimTtlMs()));
        if (claimed == null) {
            // 抢占只挑「在途 + 未被有效占位」的行：这里失败说明轮询节点正在处理它
            return missed(current, "任务正被轮询节点处理，请稍后刷新后重试");
        }
        runner.settle(claimed, AsyncTaskEvent.CANCEL);

        AsyncTaskDto after = taskService.getByTaskCode(current.taskCode());
        boolean changed = statusOf(after) == AsyncTaskStatusEnum.CANCELLED;
        log.warn("Async task cancelled by operator: taskCode={} from={} changed={}",
                current.taskCode(), status, changed);
        return of(after, changed, changed ? "任务已终止" : "终止未生效（租约已易主），请刷新后重试");
    }

    /**
     * 把一条失败 / 超时的任务重新打开（{@code PENDING}，{@code attempts} 归零，轮次 +1）。
     * {@code expires_at} 在此按行上快照（{@code poll_spec_json}）重算，不取当前档案。
     *
     * @return 动作后的任务状态；任务不存在返回 {@code null}
     */
    public AsyncTaskOperationResponse retry(String taskCode) {
        AsyncTaskDto current = taskService.getByTaskCode(normalize(taskCode));
        if (current == null) {
            return null;
        }
        AsyncTaskStatusEnum status = statusOf(current);
        if (status == null) {
            return missed(current, "任务状态码无法识别，请人工核对这一行");
        }
        if (!status.retryable()) {
            return missed(current, "只有失败或超时的任务可以重试，当前为：" + status.label());
        }
        AsyncTaskSpec spec = specOf(current);
        if (spec == null || spec.timeoutMinutes() <= 0) {
            return missed(current, "任务快照不可解析，无法重算超时窗口");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean reopened = taskService.reopen(current.id(), now,
                now.plusMinutes(spec.timeoutMinutes()));

        AsyncTaskDto after = taskService.getByTaskCode(current.taskCode());
        if (reopened) {
            log.warn("Async task reopened by operator: taskCode={} from={} round={}",
                    current.taskCode(), status, after == null ? null : after.retryRound());
        }
        return of(after, reopened, reopened
                ? "任务已重新进入轮询队列"
                : "任务状态已变化（可能刚被重试或被轮询推进），请刷新");
    }

    /**
     * 立即执行一次「按保留窗口清理终态行」（与自动家务同口径）。
     * 不接受自定义时间窗；保留期由配置 {@code knot.gateway.async-task.retention-days} 决定。
     *
     * @return 本次删除的行数
     */
    public int cleanup() {
        return runner.cleanup();
    }

    private AsyncTaskOperationResponse missed(AsyncTaskDto task, String message) {
        return of(task, false, message);
    }

    private AsyncTaskOperationResponse of(AsyncTaskDto task, boolean changed, String message) {
        if (task == null) {
            // 极端情形：动作成功但随后被清理拍删掉。不要编造一行状态，如实缺席
            return new AsyncTaskOperationResponse(null, null, null, changed, message, null);
        }
        AsyncTaskStatusEnum status = statusOf(task);
        return new AsyncTaskOperationResponse(task.taskCode(), task.status(),
                status == null ? null : status.label(), changed, message, task.retryRound());
    }

    /** 未知状态码返回 {@code null}（不静默给默认值——那会让人以为状态是对的） */
    private static AsyncTaskStatusEnum statusOf(AsyncTaskDto task) {
        return task == null ? null : AsyncTaskStatusEnum.fromCode(task.status());
    }

    private static AsyncTaskSpec specOf(AsyncTaskDto task) {
        return StringUtils.isBlank(task.pollSpecJson())
                ? null
                : JsonKit.fromJson(task.pollSpecJson(), AsyncTaskSpec.class);
    }

    /**
     * 一次运维动作一个占位号，带节点前缀便于事后追：「这行被谁终止的」。
     * 与轮询器同口径（{@code nodeId-uuid}），只是中间多一个 {@code -admin-} 标记。
     */
    private String newClaimId() {
        String nodeId = StringUtils.isBlank(properties.getNodeId())
                ? "gateway"
                : properties.getNodeId().trim();
        return nodeId + CLAIM_MARKER + UUID.randomUUID();
    }

    private static String normalize(String taskCode) {
        return taskCode == null ? null : taskCode.trim();
    }
}
