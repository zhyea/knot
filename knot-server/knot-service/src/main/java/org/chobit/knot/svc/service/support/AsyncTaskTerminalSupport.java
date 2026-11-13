package org.chobit.knot.svc.service.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 查询响应 → 终态判定（唯一出口）：成功 / 失败 / 仍在进行，附带产物与失败原因。
 * 取值取不到 = {@code null}（上游还没出结果），判定求值失败 = 抛（配置写错）；本类不做 try/catch 兜底，兜底只在试跑那一层做。
 */
@Slf4j
public final class AsyncTaskTerminalSupport {

    private AsyncTaskTerminalSupport() {
    }

    /**
     * 判定顺序即优先级：成功 → 失败 → 进行；取不到状态文本时按「仍在进行」处理，连续取不到由 {@code max_attempts} / 超时兜住。
     * 产物只在成功时取；失败原因只在失败时取，其余情况为 {@code null}，由调用方决定兜底文案。
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
