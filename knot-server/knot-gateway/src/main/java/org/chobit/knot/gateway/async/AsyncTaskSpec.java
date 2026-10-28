package org.chobit.knot.gateway.async;

import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;

/**
 * 查询口径快照（落 {@code kb_async_tasks.poll_spec_json}）。
 *
 * <p><b>为什么要快照而不是终态时回查档案</b>：档案是「当前配置」，在途任务必须按
 * <b>提交时</b>的口径轮询与计费 —— 否则改一次地址/终态取值，就会让一批在途任务
 * 查不到或判错终态，且金额无法复现。这是「同一笔任务无论何时完成，算法可复现」的落地方式。</p>
 *
 * <p>⭐ <b>本记录是纯数据，不含任何求值逻辑</b>（2026-10-28）：{@code taskIdPath} /
 * {@code statusPath} / {@code resultPath} 是 <b>MVEL 取值表达式</b>；
 * {@code statusSucceeded} / {@code statusFailed} / {@code statusRunning} 是
 * <b>MVEL 布尔表达式</b>（可用变量 {@code status} 与 {@code body}）。
 * 求值统一走 {@link AsyncTaskExpressionSupport}，判定语义统一在
 * {@link AsyncTaskSupport#terminalOf}——三处各写一份比较逻辑必然漂移
 * （旧版把「逗号分隔取值是否包含」写在记录里，正是本次被替换的形态）。</p>
 *
 * <p>{@code queryPath} 里的 {@code {task_id}} 是 <b>URL 模板占位</b>，与上面的 MVEL 表达式
 * 不是一回事（一个拼地址、一个读响应），由 {@link AsyncTaskSupport#resolveQueryPath} 替换。</p>
 */
public record AsyncTaskSpec(String queryMethod,
                            String queryPath,
                            /** 创建响应中取上游任务 ID 的 MVEL 表达式（只在创建时用一次，一并快照以便复现） */
                            String taskIdPath,
                            /** 查询响应中取状态文本的 MVEL 表达式 */
                            String statusPath,
                            /** 判断「状态 = 成功」的 MVEL 布尔表达式，如 {@code status == 'succeeded'} */
                            String statusSucceeded,
                            /** 判断「状态 = 失败」的 MVEL 布尔表达式 */
                            String statusFailed,
                            /** 判断「状态 = 进行中」的 MVEL 布尔表达式；空 = 除成功/失败外一律非终态 */
                            String statusRunning,
                            /** 查询响应中取产物 URL 的 MVEL 表达式 */
                            String resultPath,
                            Integer resultExpiresDays,
                            int pollIntervalMs,
                            int pollBackoffBaseMs,
                            int pollBackoffMaxMs,
                            int maxAttempts,
                            long timeoutMs) {

    /**
     * 由档案实体投影：档案改了不影响已快照的任务。
     *
     * <p>档案列缺失或非正时按 {@link AsyncTaskDefaults} 兜底（与 {@link AsyncTaskProperties} 同一个出处），
     * 本方法不再写裸字面量。</p>
     */
    public static AsyncTaskSpec of(AsyncTaskProfileEntity profile) {
        if (profile == null) {
            return null;
        }
        return new AsyncTaskSpec(
                defaultText(profile.getQueryMethod(), AsyncTaskDefaults.QUERY_METHOD),
                profile.getQueryPath(),
                profile.getTaskIdPath(),
                profile.getStatusPath(),
                profile.getStatusSucceeded(),
                profile.getStatusFailed(),
                profile.getStatusRunning(),
                profile.getResultPath(),
                profile.getResultExpiresDays(),
                positive(profile.getPollIntervalMs(), AsyncTaskDefaults.POLL_INTERVAL_MS),
                positive(profile.getPollBackoffBaseMs(), AsyncTaskDefaults.POLL_BACKOFF_BASE_MS),
                positive(profile.getPollBackoffMaxMs(), AsyncTaskDefaults.POLL_BACKOFF_MAX_MS),
                positive(profile.getMaxAttempts(), AsyncTaskDefaults.MAX_ATTEMPTS),
                profile.getTimeoutMs() == null ? AsyncTaskDefaults.TASK_TIMEOUT_MS
                        : Math.max(1L, profile.getTimeoutMs()));
    }

    /**
     * 查询路径占位替换：{@code {task_id}}。
     *
     * <p>替换不完（还有 {@code {}）就直接抛：拼出来的是一个查不到东西的 URL，
     * 表现为「任务永远查不到」，远比一次明确的失败难排查。</p>
     */
    public String resolvePath(String upstreamTaskId) {
        if (queryPath == null) {
            throw new IllegalStateException("async task query path is not configured");
        }
        String resolved = queryPath.replace("{task_id}", upstreamTaskId == null ? "" : upstreamTaskId.trim());
        if (resolved.contains("{")) {
            throw new IllegalStateException("async task query path has unresolved placeholder: " + resolved);
        }
        return resolved;
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
