package org.chobit.knot.gateway.async;

import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;

/**
 * 查询口径快照（落 {@code kb_async_tasks.poll_spec_json}）。
 *
 * <p><b>为什么要快照而不是终态时回查档案</b>：档案是「当前配置」，在途任务必须按
 * <b>提交时</b>的口径轮询与计费 —— 否则改一次地址/终态取值，就会让一批在途任务
 * 查不到或判错终态，且金额无法复现。这是「同一笔任务无论何时完成，算法可复现」的落地方式。</p>
 *
 * <p>路径里的 {@code {task_id}} / {@code {external_task_id}} 占位由
 * {@link AsyncTaskSupport#resolveQueryPath} 在查询时替换。</p>
 */
public record AsyncTaskSpec(String queryMethod,
                            String queryPath,
                            /** 创建响应中取上游任务 ID 的路径（只在创建时用一次，一并快照以便复现） */
                            String taskIdPath,
                            /**
                             * 创建请求体中承载「我方任务标识」的字段路径（如 {@code options.external_task_id}）；
                             * 为空表示上游不支持，或该绑定未启用反查。
                             *
                             * <p>⚠ 取值约定：由请求适配层（如 {@code KlingVideoSupport}）在出站前写入，
                             * 异步层<b>不回头读上游体</b>，两侧同取 {@code context.requestId()}。
                             * 若配了字段但适配层没写同名字段，反查会查空——不错账，只是恢复失效。</p>
                             */
                            String externalTaskField,
                            String statusPath,
                            String statusSucceeded,
                            String statusFailed,
                            String statusRunning,
                            String resultPath,
                            Integer resultExpiresDays,
                            int pollIntervalMs,
                            int pollBackoffBaseMs,
                            int pollBackoffMaxMs,
                            int maxAttempts,
                            long timeoutMs) {

    /** 由档案实体投影：档案改了不影响已快照的任务 */
    public static AsyncTaskSpec of(AsyncTaskProfileEntity profile) {
        if (profile == null) {
            return null;
        }
        return new AsyncTaskSpec(
                defaultText(profile.getQueryMethod(), "GET"),
                profile.getQueryPath(),
                profile.getTaskIdPath(),
                profile.getExternalTaskField(),
                profile.getStatusPath(),
                profile.getStatusSucceeded(),
                profile.getStatusFailed(),
                profile.getStatusRunning(),
                profile.getResultPath(),
                profile.getResultExpiresDays(),
                positive(profile.getPollIntervalMs(), 5000),
                positive(profile.getPollBackoffBaseMs(), 1000),
                positive(profile.getPollBackoffMaxMs(), 30000),
                positive(profile.getMaxAttempts(), 360),
                profile.getTimeoutMs() == null ? 1_800_000L : Math.max(1L, profile.getTimeoutMs()));
    }

    /**
     * 查询路径占位替换：{@code {task_id}} / {@code {external_task_id}}。
     *
     * <p>替换不完（还有 {@code {}）就直接抛：拼出来的是一个查不到东西的 URL，
     * 表现为「任务永远查不到」，远比一次明确的失败难排查。</p>
     */
    public String resolvePath(String upstreamTaskId, String externalTaskId) {
        String path = queryPath == null ? null : queryPath;
        if (path == null) {
            throw new IllegalStateException("async task query path is not configured");
        }
        String resolved = path.replace("{task_id}", upstreamTaskId == null ? "" : upstreamTaskId.trim())
                .replace("{external_task_id}", externalTaskId == null ? "" : externalTaskId.trim());
        if (resolved.contains("{")) {
            throw new IllegalStateException("async task query path has unresolved placeholder: " + resolved);
        }
        return resolved;
    }

    /** 终态取值是否在成功集合内（逗号分隔多值，忽略大小写与空白） */
    public boolean succeeded(String status) {
        return contains(statusSucceeded, status);
    }

    public boolean failed(String status) {
        return contains(statusFailed, status);
    }

    /**
     * 是否明确声明为非终态。
     *
     * <p>{@code statusRunning} 为空表示「除成功/失败外一律视为非终态」——
     * 上游新增一个中间态（如 {@code queued}）时不需要改配置。</p>
     */
    public boolean running(String status) {
        if (statusRunning == null || statusRunning.isBlank()) {
            return true;
        }
        return contains(statusRunning, status);
    }

    private static boolean contains(String candidates, String status) {
        if (candidates == null || candidates.isBlank() || status == null) {
            return false;
        }
        String normalized = status.trim();
        for (String candidate : candidates.split(",")) {
            if (candidate.trim().equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
