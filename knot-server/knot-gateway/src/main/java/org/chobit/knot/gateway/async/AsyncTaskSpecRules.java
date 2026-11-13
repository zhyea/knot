package org.chobit.knot.gateway.async;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.usage.UsageRawReader;
import org.chobit.knot.svc.service.support.AsyncTaskExpressionSupport;
import org.chobit.knot.svc.service.support.AsyncTaskTerminalSupport;

import java.util.List;
import java.util.Map;

/**
 * 档案（{@link AsyncTaskSpec}）的求值规则：终态判定、产物地址、退避时长、上游任务 ID。
 * 异步能力唯一的厂商知识边界：查询路径、终态取值、产物位置全部来自档案（{@code kb_async_task_profiles}）配置值，新增一家异步上游 = 加一行档案，不改本类；纯函数（无 state、无 Spring、无 IO）。
 * 判定与求值下沉 {@code svc}：{@code AsyncTaskTerminalSupport#verdict} 同时服务网关（按快照判在途任务）与管理端试跑（按草稿判），本类只留快照视角的适配。
 */
public final class AsyncTaskSpecRules {

    private AsyncTaskSpecRules() {
    }

    /**
     * 从创建响应里取上游任务 ID；取不到直接抛，绝不返回 {@code null}。
     * 用档案的 {@code task_id_path}（MVEL 取值表达式），与拼查询 URL 的 {@code query_path} 是两条不同路径（一个读创建响应、一个拼请求地址）。
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
     * 判定口径全部来自档案里的三条 MVEL 表达式（变量 {@code status} 与整个 {@code body}）；顺序即优先级（成功 → 失败 → 进行），实现在 {@link AsyncTaskTerminalSupport#verdict}。
     */
    public static AsyncTaskTerminalSupport.Terminal terminalOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        return AsyncTaskTerminalSupport.verdict(queryBody, spec.expressions()).terminal();
    }

    /**
     * 终态产物地址（可能多个）：视频协议一次生成 N 个产物时全部保留。
     * 归档只搬第一个（{@code ArchiveStore} 是单产物旁路），结果 JSON 里存全部。
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
     * 取结算用的上游 usage 对象：配了 {@code usagePath} 就按档案表达式从查询响应里取，否则回落顶层 {@code usage}。
     * 配置优先于顶层 usage；未配置时走 {@link UsageRawReader#readUsage}，与同步链路同一口径（含 Responses 协议 {@code response.usage} 下钻兜底）。
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
     * 下一次轮询的退避时长（毫秒）：指数退避 + 上限，与 {@code BackoffCalculator} 同公式（{@code min(cap, base × 2^(n-1))}）。
     * 节奏参数来自任务档案，语义是「多久再看一眼」，不复用规则级 {@code RetryPolicy}。
     */
    public static long nextDelayMs(int attempts, AsyncTaskSpec spec) {
        int exponent = Math.max(0, attempts - 1);
        long delay = (long) (spec.pollBackoffBaseMs() * Math.pow(2, Math.min(exponent, 10)));
        return Math.max(spec.pollIntervalMs(), Math.min(spec.pollBackoffMaxMs(), delay));
    }
}
