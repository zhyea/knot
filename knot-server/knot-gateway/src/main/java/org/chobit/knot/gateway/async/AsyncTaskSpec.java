package org.chobit.knot.gateway.async;

import org.chobit.knot.svc.dto.async.AsyncTaskProfileDto;
import org.chobit.knot.svc.service.support.AsyncTaskExpressions;

/**
 * 查询口径快照（落 {@code kb_async_tasks.poll_spec_json}），在途任务按提交时的口径轮询与计费。
 * 纯数据，不含求值逻辑：{@code taskIdPath} / {@code statusPath} / {@code resultPath} / {@code errorMessagePath} 为 MVEL 取值表达式，{@code statusSucceeded} / {@code statusFailed} / {@code statusRunning} 为 MVEL 布尔表达式（变量 {@code status} 与 {@code body}）；求值走 {@code AsyncTaskExpressionSupport}，判定走 {@code AsyncTaskTerminalSupport#verdict}。
 * 旧快照反序列化时 {@code errorMessagePath} 为 {@code null}，运行时按老行为兜底。
 * {@code queryPath} 里的 {@code {task_id}} 是 URL 模板占位（一个拼地址、一个读响应），由 {@link #resolvePath(String)} 替换。
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
                            /** 查询响应中取产物地址的 MVEL 表达式（标量 = 单个，数组 = 多个） */
                            String resultPath,
                            /** 产物是对象数组时取哪个字段作为地址；空 = 元素必须是标量 */
                            String resultUrlField,
                            /** 查询响应中取失败原因的 MVEL 表达式；空 = 沿用顶层 {@code message} */
                            String errorMessagePath,
                            Integer resultExpiresDays,
                            int pollIntervalMs,
                            int pollBackoffBaseMs,
                            int pollBackoffMaxMs,
                            int maxAttempts,
                            /** knot 侧任务寿命（分钟）；快照与档案同单位，网关侧只做 {@code plusMinutes} */
                            long timeoutMinutes,
                            /** 查询响应取证：取上游 usage 对象的 MVEL 表达式（如 {@code data.usage}）；空 = 沿用顶层 usage。旧快照缺该字段时为 null，走兜底 */
                            String usagePath) {

    /**
     * 由档案实体投影：档案改了不影响已快照的任务。
     * 档案列缺失或非正时按 {@link AsyncTaskDefaults} 兜底（与 {@link AsyncTaskProperties} 同一个出处），本方法不写裸字面量。
     */
    public static AsyncTaskSpec of(AsyncTaskProfileDto profile) {
        if (profile == null) {
            return null;
        }
        return new AsyncTaskSpec(
                defaultText(profile.queryMethod(), AsyncTaskDefaults.QUERY_METHOD),
                profile.queryPath(),
                profile.taskIdPath(),
                profile.statusPath(),
                profile.statusSucceeded(),
                profile.statusFailed(),
                profile.statusRunning(),
                profile.resultPath(),
                profile.resultUrlField(),
                profile.errorMessagePath(),
                profile.resultExpiresDays(),
                positive(profile.pollIntervalMs(), AsyncTaskDefaults.POLL_INTERVAL_MS),
                positive(profile.pollBackoffBaseMs(), AsyncTaskDefaults.POLL_BACKOFF_BASE_MS),
                positive(profile.pollBackoffMaxMs(), AsyncTaskDefaults.POLL_BACKOFF_MAX_MS),
                positive(profile.maxAttempts(), AsyncTaskDefaults.MAX_ATTEMPTS),
                profile.timeoutMinutes() == null ? AsyncTaskDefaults.TASK_TIMEOUT_MINUTES
                        : Math.max(1L, profile.timeoutMinutes()),
                profile.usagePath());
    }

    /**
     * 七条表达式里用于终态判定的视图：交给 {@code AsyncTaskTerminalSupport} 判定。
     * 不含 {@code usagePath}（取上游 usage 的 {@code Map}，只在结算侧消费）；旧快照缺该字段时反序列化为 {@code null}，运行时按顶层 usage 兜底。
     * 快照与档案都投影成同一个值对象，判定实现只有一处。
     */
    public AsyncTaskExpressions expressions() {
        return new AsyncTaskExpressions(statusPath, statusSucceeded, statusFailed,
                statusRunning, resultPath, resultUrlField, errorMessagePath);
    }

    /**
     * 查询路径占位替换：{@code {task_id}}；替换不完（还有 {@code {}}）就直接抛。
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
