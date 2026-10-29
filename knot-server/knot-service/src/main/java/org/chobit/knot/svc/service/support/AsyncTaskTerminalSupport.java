package org.chobit.knot.svc.service.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 查询响应 → 终态判定（<b>唯一出口</b>）：成功 / 失败 / 仍在进行，附带产物与失败原因。
 *
 * <p>⭐⭐ <b>为什么要从 {@code gateway.async.AsyncTaskSpecRules} 里搬出来</b>（2026-10-29）：
 * 判定的消费方从一个变成两个 —— 网关按<b>快照</b>判在途任务，管理端按<b>草稿档案</b>做试跑。
 * 若管理端各自复写一份「先成功、再失败、再进行」的顺序，试跑通过而运行时判错就是必然结局，
 * 而这类错误的现象是「任务永远 RUNNING 到超时」，最难查。判据见 {@code 10-backend-java.md §3.2}：
 * 两个 app 都用 → 归 {@code svc}。</p>
 *
 * <p>⚠ <b>失败口径刻意不对称</b>（{@code 60-business-rules.md} §8.14）：取值取不到 = {@code null}
 * （上游还没出结果），判定求值失败 = 抛（配置写错）。本类不做任何 try/catch 兜底，
 * 兜底只在试跑那一层做——那里要把错误展示给人看，而运行时必须响。</p>
 */
@Slf4j
public final class AsyncTaskTerminalSupport {

    private AsyncTaskTerminalSupport() {
    }

    /**
     * 判定顺序即优先级：<b>成功 → 失败 → 进行</b>。
     *
     * <p>取不到状态文本时按「仍在进行」处理：上游瞬时抖动、响应体尚未成形都不该把任务判死；
     * 连续取不到会被 {@code max_attempts} / 超时兜住。</p>
     *
     * <p>产物只在成功时取（失败响应里的 {@code url} 字段毫无意义，取出来会把半成品的地址
     * 当作交付物）；失败原因只在失败时取，其余情况为 {@code null}，由调用方决定兜底文案。</p>
     */
    public static Verdict verdict(Map<String, Object> queryBody, AsyncTaskExpressions expressions) {
        String status = AsyncTaskExpressionSupport.text(queryBody, expressions.statusPath());
        if (status == null) {
            return new Verdict(null, Terminal.RUNNING, List.of(), null);
        }
        Map<String, Object> vars = Map.of("status", status, "body", queryBody);
        if (AsyncTaskExpressionSupport.test(vars, expressions.statusSucceeded())) {
            return new Verdict(status, Terminal.SUCCEEDED,
                    AsyncTaskExpressionSupport.texts(queryBody, expressions.resultPath(),
                            expressions.resultUrlField()), null);
        }
        if (AsyncTaskExpressionSupport.test(vars, expressions.statusFailed())) {
            return new Verdict(status, Terminal.FAILED, List.of(),
                    AsyncTaskExpressionSupport.text(queryBody, expressions.errorMessagePath()));
        }
        if (StringUtils.isBlank(expressions.statusRunning())) {
            // 没配 = 「除成功/失败外一律非终态」：上游新增中间态（queued 等）不需要改档案
            return new Verdict(status, Terminal.RUNNING, List.of(), null);
        }
        if (AsyncTaskExpressionSupport.test(vars, expressions.statusRunning())) {
            return new Verdict(status, Terminal.RUNNING, List.of(), null);
        }
        // 配了运行态却不匹配：上游多了一个谁都不认识的状态。仍然按「进行」处理（不判死），
        // 但必须留下信号——否则这个状态会一直被当成正常轮询，直到超时才显形。
        log.warn("Async task status matched neither terminal nor running expression: status={} running={}",
                status, expressions.statusRunning());
        return new Verdict(status, Terminal.RUNNING, List.of(), null);
    }

    /** 单次查询的判定结果 */
    public enum Terminal {
        RUNNING, SUCCEEDED, FAILED
    }

    /**
     * 判定结果。
     *
     * @param status       档案 {@code status_path} 取到的原文（取不到为 {@code null}，试跑要展示它）
     * @param terminal     三值判定
     * @param resultUrls   产物地址（成功时非空才可交付；失败/进行中恒为空）
     * @param errorMessage 失败原因原文（仅失败时有值，取不到为 {@code null}）
     */
    public record Verdict(String status, Terminal terminal, List<String> resultUrls, String errorMessage) {

        /** 是否拿到可交付产物：成功但取不到产物要与「失败」区分开 */
        public boolean hasResult() {
            return terminal == Terminal.SUCCEEDED && !resultUrls.isEmpty();
        }
    }
}
