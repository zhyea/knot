package org.chobit.knot.gateway.async;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.AsyncTaskDeliveryStatusEnum;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.service.AsyncTaskService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回调交付的<b>编排</b>：抢占 → 投递 → 成功落定 / 失败退避 / 到上限放弃。
 *
 * <p>⭐ 它是「交付」这条链路的唯一推进点，与轮询的 {@link AsyncTaskRunner} 平级且互不依赖：
 * 轮询把任务推到终态并<b>武装</b>交付；交付在终态之后独立跑，二者只共享一行数据。</p>
 *
 * <h3>三条从参考设计借鉴、并在此落地的原则</h3>
 * <ol>
 *   <li><b>交付是独立维度</b>：进度写 {@code delivery_status / delivery_attempts}，
 *       从不改 {@code status}。于是「生成成功 + 回调失败」是可表达的合法组合，
 *       而不会出现「终态既代表回调失败、又代表任务失败」的自相矛盾。</li>
 *   <li><b>可崩溃恢复（检查点）</b>：投递进度全在库里。节点拿到租约后崩溃，
 *       行上的 {@code delivery_claim_expires_at} 到期即可被下一拍重新抢占续投——
 *       不需要任何补偿任务，也不会因为「进程没了」而永久漏回调。</li>
 *   <li><b>正向包含扫描</b>：抢占只看 {@code delivery_status IN (待回调)}（
 *       {@link AsyncTaskDeliveryStatusEnum#deliverableCodes()}），不用「排除成功/失败」。
 *       后者加一个新子态就会被静默扫不到。</li>
 * </ol>
 *
 * <p>⚠ <b>租约是兑付口</b>：{@code completeDelivery / rescheduleDelivery} 都带
 * {@code delivery_claim_id} 条件。少了它，租约过期的原节点仍能写，等于把新主人刚立的占位抹掉，
 * 第三个节点再领一次同一行——同一笔结果被重复回调给客户。</p>
 *
 * <p>⚠ <b>至少一次，不是恰好一次</b>：投递是「发出去」再「记成功」，两步之间崩溃会导致重发。
 * 因此回调 body 带稳定的 {@code task_id}，客户端须按 {@code task_id} 幂等——这是 webhook 的通用约定，
 * 本类不承诺恰好一次。</p>
 */
@Slf4j
public class AsyncTaskDeliveryRunner {

    private final AsyncTaskService taskService;
    private final AsyncTaskCallbackPort callbackPort;
    private final AsyncTaskDeliveryPolicy policy;

    public AsyncTaskDeliveryRunner(AsyncTaskService taskService,
                                   AsyncTaskCallbackPort callbackPort,
                                   AsyncTaskDeliveryPolicy policy) {
        this.taskService = taskService;
        this.callbackPort = callbackPort;
        this.policy = policy;
    }

    /**
     * 抢占一批到期待投递任务。
     *
     * <p>⚠ 返回后必须在租约有效期内处理完（{@code delivery-claim-ttl-ms}），
     * 否则行会被别的节点回收，本轮的写入被 CAS 挡掉，表现为「投了但没记上」。</p>
     */
    public List<AsyncTaskDto> claimDue(String claimId) {
        return taskService.claimDeliveries(claimId, Duration.ofMillis(policy.claimTtlMs()),
                policy.batchSize());
    }

    /**
     * 投递一条任务并落定结果。
     *
     * @param task    已持有交付租约的行
     * @param claimId 本轮交付占位号
     * @return 是否成功送达并记上（{@code false} = 本次失败已排退避/放弃，或租约已易主）
     */
    public boolean deliver(AsyncTaskDto task, String claimId) {
        String url = task.callbackUrl();
        if (StringUtils.isBlank(url)) {
            // 理论上不可达：无 callback_url 的行 delivery_status = NONE，扫描不到。
            // 真出现说明数据被手工改过——收口成 NONE 停止空转，并留下可用于事后排查的日志。
            log.error("Async task delivery claimed without callback url, stop delivery: taskCode={}",
                    task.taskCode());
            taskService.completeDelivery(task.id(), claimId, AsyncTaskDeliveryStatusEnum.NONE.code(),
                    attempts(task), null, "callback url missing");
            return false;
        }
        int attempt = attempts(task) + 1;
        try {
            callbackPort.deliver(url, AsyncTaskViewSupport.toResponse(task));
        } catch (RuntimeException e) {
            return onFailure(task, claimId, url, attempt, e);
        }
        boolean settled = taskService.completeDelivery(task.id(), claimId,
                AsyncTaskDeliveryStatusEnum.SUCCEEDED.code(), attempt, LocalDateTime.now(), null);
        if (settled) {
            log.info("Async task callback delivered: taskCode={} attempt={}", task.taskCode(), attempt);
        } else {
            // 投递已经发出去了，但租约在写回前易主：不谎报成功，由新主人按未送达重试（可能重复一次）
            log.warn("Async task callback delivered but not recorded (claim lost): taskCode={}", task.taskCode());
        }
        return settled;
    }

    private boolean onFailure(AsyncTaskDto task, String claimId, String url, int attempt, RuntimeException e) {
        String reason = StringUtils.abbreviate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 1000);
        if (attempt >= Math.max(1, policy.maxAttempts())) {
            log.error("Async task callback gave up after {} attempts: taskCode={} url={}",
                    attempt, task.taskCode(), url, e);
            taskService.completeDelivery(task.id(), claimId, AsyncTaskDeliveryStatusEnum.FAILED.code(),
                    attempt, null, reason);
            return false;
        }
        LocalDateTime next = LocalDateTime.now().plusNanos(nextDelayMs(attempt) * 1_000_000L);
        log.warn("Async task callback failed, will retry: taskCode={} attempt={} nextDeliveryAt={}",
                task.taskCode(), attempt, next, e);
        taskService.rescheduleDelivery(task.id(), claimId, attempt, next, reason);
        return false;
    }

    /**
     * 第 {@code attempt} 次失败后的退避时长：{@code min(cap, base × 2^(n-1))}。
     *
     * <p>与 {@link AsyncTaskSpecRules#nextDelayMs} 同公式但<b>不共用参数</b>：
     * 一个是「多久再查上游」，一个是「多久再打扰客户端」，共用会让调轮询节奏顺手改掉回调节奏。</p>
     */
    long nextDelayMs(int attempt) {
        int exponent = Math.max(0, attempt - 1);
        long delay = (long) (policy.backoffBaseMs() * Math.pow(2, Math.min(exponent, 10)));
        return Math.min(policy.backoffMaxMs(), delay);
    }

    private static int attempts(AsyncTaskDto task) {
        return task.deliveryAttempts() == null ? 0 : task.deliveryAttempts();
    }
}
