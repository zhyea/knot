package org.chobit.knot.gateway.async;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.usage.UsageRawReader;
import org.chobit.knot.svc.service.support.AsyncTaskExpressionSupport;
import org.chobit.knot.svc.service.support.AsyncTaskTerminalSupport;

import java.util.List;
import java.util.Map;

/**
 * 档案（{@link AsyncTaskSpec}）的求值规则：终态判定、产物地址、退避时长、上游任务 ID。
 *
 * <p>⭐ <b>本类是异步能力唯一的厂商知识边界</b>：查询路径、终态取值、产物位置全部来自档案
 * （{@code kb_async_task_profiles}）的配置值，新增一家异步上游 = 加一行档案，<b>不改变这里任何一行</b>。
 * 所以它是纯函数（无 state、无 Spring、无 IO）。</p>
 *
 * <p>⭐ <b>判定与求值已下沉 {@code svc}</b>（2026-10-29）：{@code AsyncTaskTerminalSupport#verdict}
 * 现在同时服务网关（按快照判在途任务）与管理端试跑（按草稿判）。本类只留「快照视角的适配」——
 * 从这里仍能一眼看出「一次轮询要问上游什么」，但比较逻辑不再有两份。</p>
 */
public final class AsyncTaskSpecRules {

    private AsyncTaskSpecRules() {
    }

    /**
     * 从创建响应里取上游任务 ID；<b>取不到直接抛，绝不返回 {@code null}</b>（2026-11-01）。
     *
     * <p>⚠ 用的是档案的 {@code task_id_path}（MVEL 取值表达式），与拼查询 URL 的
     * {@code query_path} 是两条不同的路径（一个读创建响应、一个拼请求地址）。
     * 混用会让「取不到 ID」表现为「查不到任务」，排查方向整个偏掉。</p>
     *
     * <p>⭐⭐ <b>为什么这里不能用宽容口径回 {@code null}</b>：走到本方法说明绑定上
     * {@code async_profile_code} 有值、档案也读到了——即「这次该建成一条异步任务」已成事实。
     * 此时响应里没有任务 ID，只有两种可能：档案 {@code task_id_path} 写错，或上游契约变了。
     * 回 {@code null} 只能让调用方回退同步，把「任务已创建」的响应当成最终结果交给客户端，
     * 而本地没有任何任务行可轮询——既查不到也不计费。静默失败远比一次明确的 502 糟。</p>
     *
     * @return 非空的上游任务 ID
     * @throws IllegalStateException 表达式为空 / 求值失败 / 取到的不是标量
     */
    public static String upstreamTaskIdOf(Map<String, Object> createBody, AsyncTaskSpec spec) {
        return AsyncTaskExpressionSupport.requireText(createBody, spec.taskIdPath(),
                "异步任务档案的上游任务 ID（task_id_path）");
    }

    /**
     * 终态判定：成功 / 失败 / 仍在进行。
     *
     * <p>判定口径全部来自档案里的三条 MVEL 表达式，变量是 {@code status} 与整个 {@code body}。
     * 顺序即优先级（成功 → 失败 → 进行），实现在 {@link AsyncTaskTerminalSupport#verdict}。</p>
     */
    public static AsyncTaskTerminalSupport.Terminal terminalOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        return AsyncTaskTerminalSupport.verdict(queryBody, spec.expressions()).terminal();
    }

    /**
     * 终态产物地址（可能多个）：视频协议一次生成 N 个产物时全部保留。
     *
     * <p>⚠ 归档只搬第一个（{@code ArchiveStore} 是单产物旁路），但<b>结果 JSON 里要存全部</b>：
     * 客户端拿到的是完整结果，少存就是少交付。</p>
     */
    public static List<String> resultUrlsOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        return AsyncTaskExpressionSupport.texts(queryBody, spec.resultPath(), spec.resultUrlField());
    }

    /** 首个产物地址；没有产物返回 null（成功但无产物由调用方按失败处理） */
    public static String resultUrlOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        List<String> urls = resultUrlsOf(queryBody, spec);
        return urls.isEmpty() ? null : urls.get(0);
    }

    /**
     * 失败原因原文（仅失败终态有意义）；档案没配 {@code error_message_path} 时返回 null，
     * 由调用方回落到「顶层 {@code message}」这个既有行为。
     */
    public static String errorMessageOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        return AsyncTaskExpressionSupport.text(queryBody, spec.errorMessagePath());
    }

    /**
     * 取结算用的上游 usage 对象：配了 {@code usagePath} 就按档案表达式从查询响应里取，
     * 否则回落顶层 {@code usage}。
     *
     * <p>⭐ <b>配置优先于顶层 usage</b>：异步上游常把 usage（含 cost）嵌在 {@code data.*}，
     * 顶层没有，结算取不到就会漏计费。未配置时走 {@link UsageRawReader#readUsage}，
     * 与同步链路同一口径（含 Responses 协议 {@code response.usage} 下钻兜底），不另写一套解析。</p>
     *
     * @return 上游 usage 的 {@code Map}；上游没报 usage 时返回 {@code null}
     */
    public static Map<String, Object> usageOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        if (spec != null && StringUtils.isNotBlank(spec.usagePath())) {
            return AsyncTaskExpressionSupport.object(queryBody, spec.usagePath());
        }
        return UsageRawReader.readUsage(queryBody);
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
}
