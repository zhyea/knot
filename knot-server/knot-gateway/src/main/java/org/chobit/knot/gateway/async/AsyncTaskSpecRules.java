package org.chobit.knot.gateway.async;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * 档案（{@link AsyncTaskSpec}）的求值规则：终态判定、产物地址、退避时长、上游任务 ID。
 *
 * <p>⭐ <b>本类是异步能力唯一的厂商知识边界</b>：查询路径、终态取值、产物位置全部来自档案
 * （{@code kb_async_task_profiles}）的配置值，新增一家异步上游 = 加一行档案，<b>不改变这里任何一行</b>。
 * 所以它是纯函数（无 state、无 Spring、无 IO），只依赖 {@code AsyncTaskExpressionSupport} 求值。</p>
 *
 * <p>⭐ <b>为什么从 {@code AsyncTaskSupport} 里单独拆出来</b>（2026-10-29）：不是搬家，是<b>分层</b>。
 * 那个类管提交侧（读绑定 → 读档案 → 建任务行），依赖模型/绑定/计费规则与本地缓存；本类只对
 * <b>已有快照求值</b>，输入是 {@code Map} 与 {@link AsyncTaskSpec}，无 state、无 IO、无 Spring。
 * 两种抽象层级混在一个类里，结果是「改退避算法」和「改绑定解析」看起来是同一件事，
 * 而前者其实可以被单测穷举、后者必须起上下文。</p>
 */
@Slf4j
public final class AsyncTaskSpecRules {

    private AsyncTaskSpecRules() {
    }

    /**
     * 从创建响应里取上游任务 ID；取不到返回 null。
     *
     * <p>⚠ 用的是档案的 {@code task_id_path}（MVEL 取值表达式），与拼查询 URL 的
     * {@code query_path} 是两条不同的路径（一个读创建响应、一个拼请求地址）。
     * 混用会让「取不到 ID」表现为「查不到任务」，排查方向整个偏掉。</p>
     */
    public static String upstreamTaskIdOf(Map<String, Object> createBody, AsyncTaskSpec spec) {
        return AsyncTaskExpressionSupport.text(createBody, spec.taskIdPath());
    }

    /**
     * 终态判定：成功 / 失败 / 仍在进行。
     *
     * <p>判定口径全部来自档案里的三条 MVEL 表达式（{@code status_succeeded} / {@code status_failed} /
     * {@code status_running}），变量是 {@code status}（{@link AsyncTaskSpec#statusPath()} 取到的文本）
     * 与 {@code body}（整个查询响应，供「成功态与失败原因不在同一层」这类档案使用）。</p>
     *
     * <p>顺序即优先级：成功 → 失败 → 进行。<b>取不到状态时按「仍在进行」处理</b>：
     * 上游瞬时抖动、响应体尚未成形都不该把任务判死；连续取不到会被 {@code max_attempts} / 超时兜住。</p>
     */
    public static Terminal terminalOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        String status = AsyncTaskExpressionSupport.text(queryBody, spec.statusPath());
        if (status == null) {
            return Terminal.RUNNING;
        }
        Map<String, Object> vars = Map.of("status", status, "body", queryBody);
        if (AsyncTaskExpressionSupport.test(vars, spec.statusSucceeded())) {
            return Terminal.SUCCEEDED;
        }
        if (AsyncTaskExpressionSupport.test(vars, spec.statusFailed())) {
            return Terminal.FAILED;
        }
        if (StringUtils.isBlank(spec.statusRunning())) {
            // 没配 = 「除成功/失败外一律非终态」：上游新增中间态（queued 等）不需要改档案
            return Terminal.RUNNING;
        }
        if (AsyncTaskExpressionSupport.test(vars, spec.statusRunning())) {
            return Terminal.RUNNING;
        }
        // 配了运行态却不匹配：上游多了一个谁都不认识的状态。仍然按「进行」处理（不判死），
        // 但必须留下信号——否则这个状态会一直被当成正常轮询，直到超时才显形。
        log.warn("Async task status matched neither terminal nor running expression: status={} profile={}",
                status, spec.statusRunning());
        return Terminal.RUNNING;
    }

    /** 终态产物 URL（MVEL 取值表达式）；取不到返回 null（成功但无产物由调用方按失败处理） */
    public static String resultUrlOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        return AsyncTaskExpressionSupport.text(queryBody, spec.resultPath());
    }

    /**
     * 下一次轮询的退避时长（毫秒）：指数退避 + 上限。
     *
     * <p>与 {@code BackoffCalculator} 同公式（{@code min(cap, base × 2^(n-1))}），
     * 不复用它，是因为那条链按规则级 {@code RetryPolicy} 建模（含 jitter / Retry-After / 状态码判定），
     * 而轮询的节奏参数来自任务档案，语义是「多久再看一眼」而不是「失败重试」。</p>
     */
    public static long nextDelayMs(int attempts, AsyncTaskSpec spec) {
        int exponent = Math.max(0, attempts - 1);
        long delay = (long) (spec.pollBackoffBaseMs() * Math.pow(2, Math.min(exponent, 10)));
        return Math.max(spec.pollIntervalMs(), Math.min(spec.pollBackoffMaxMs(), delay));
    }

    /** 单次查询的判定结果 */
    public enum Terminal {
        RUNNING, SUCCEEDED, FAILED
    }
}
