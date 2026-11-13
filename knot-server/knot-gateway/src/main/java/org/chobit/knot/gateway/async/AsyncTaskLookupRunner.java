package org.chobit.knot.gateway.async;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.AuthConstants;
import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.gateway.runtime.MdcContextSupport;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * 异步任务查询的完整业务：验 key → 查库 → 归属比对 → 投影成对外视图。
 * 阻塞段一律离开 event loop；「不是 Bearer」同步抛出，{@code requireRuleAccess} 的 401/400 以订阅后 onError 信号到达。
 * 返回 {@code Mono.empty()} 表示「没有这个任务」（句柄不存在 / 归属不符 / 上下文缺失），由 controller 映射成 404。
 * 鉴权走 {@code RoutingResolver#requireRuleAccess}，与主链路同一份实现。
 */
@Component
@RequiredArgsConstructor
public class AsyncTaskLookupRunner {

    private final AsyncTaskService taskService;
    private final RoutingResolver routingResolver;

    /**
     * 阻塞段调度器（{@code gatewayBlockingScheduler}）；单测可为 {@code null}，
     * 退化为 Reactor 共享的 {@code boundedElastic}（同 {@link AsyncTaskScheduledPoller}）。
     */
    @Nullable
    private final Scheduler blockingScheduler;

    /**
     * 按句柄查一个任务并投影成对外视图。
     * 顺序是「先验 key，再查任务」：凭证不对直接 401，不进库。
     *
     * @param authorization 客户端原始 {@code Authorization} 头（含 {@code Bearer } 前缀）
     * @param rule          客户端声明的规则码，必须与任务归属一致
     * @param taskCode      任务句柄（路径参数）
     * @param testReturnUsage {@code X-Knot-Test-Return-Usage} 原文；为 {@code true} 时叠加触发账目下发，
     *                       并额外带上游终态原文（{@code upstream_body}）。账目本身还可根据
     *                       「终态 + 消费者配置」下发，不依赖本头
     * @return 命中时是响应体；查不到 / 不属于该规则时是空 {@link Mono}（调用方映射成 404）
     * @throws GatewayAuthException 凭据缺失或无效（401）；{@code requireRuleAccess} 抛的那类
     *                              以 onError 形式在订阅后到达，见类注释
     */
    public Mono<AsyncTaskQueryResponse> lookup(String authorization, String rule, String taskCode,
                                               String testReturnUsage) {
        if (!StringUtils.startsWith(authorization, AuthConstants.BEARER_PREFIX)) {
            throw new GatewayAuthException("Invalid consumer API key");
        }
        // supplier 返回 null → 空 Mono（Reactor 契约），正好表达「查不到」，不必再造 Optional
        return MdcContextSupport.onBlockingScheduler(
                Mono.fromSupplier(() -> lookupBlocking(authorization, rule, taskCode,
                        GatewayHeaders.testReturnUsageRequested(testReturnUsage))),
                blockingScheduler);
    }

    /**
     * 阻塞段：鉴权（查库）→ 查任务（查库）→ 归属比对 → 投影。
     *
     * @return 命中时是响应体；查不到返回 {@code null}（由上层翻译成空 {@code Mono}）
     */
    private AsyncTaskQueryResponse lookupBlocking(String authorization, String rule, String taskCode,
                                                  boolean testHeader) {
        routingResolver.requireRuleAccess(authorization.substring(AuthConstants.BEARER_PREFIX.length()), rule);
        AsyncTaskDto task = StringUtils.isBlank(taskCode) ? null : taskService.getByTaskCode(taskCode.trim());
        if (task == null || !ruleMatches(task, rule)) {
            return null;
        }
        return AsyncTaskViewSupport.toResponse(task, testHeader);
    }

    /**
     * 规则归属校验：任务归属的路由规则在 {@code context_json} 里，解析后比对，不做字符串包含匹配。
     * 上下文缺失时按「不匹配」处理。
     */
    private boolean ruleMatches(AsyncTaskDto task, String rule) {
        if (StringUtils.isBlank(task.contextJson()) || StringUtils.isBlank(rule)) {
            return false;
        }
        AsyncTaskContext context = JsonKit.fromJson(task.contextJson(), AsyncTaskContext.class);
        return context != null && StringUtils.equals(rule.trim(), context.ruleCode());
    }

}
