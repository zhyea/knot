package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.AuthConstants;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskFailureResponse;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.gateway.runtime.MdcContextSupport;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.Map;

/**
 * 异步任务查询的<b>完整业务</b>：验 key → 查库 → 归属比对 → 投影成对外视图。
 *
 * <p>⭐ <b>为什么这段逻辑不在 controller 里</b>（2026-10-29 robin 拍板，规范见
 * {@code 10-backend-java.md §3.3}）：它原本整段住在 {@code GatewayTaskController} 里，
 * 于是「一次查询要满足哪些约束」被 HTTP 细节包在中间——鉴权顺序、句柄不存在与越权的同一处置、
 * 对内码到对外文案的映射，全挤在一个只能靠 WebTestClient 触达的类里。
 * 搬到 Runner 之后：业务可以单测（不必起 MVC），controller 只剩「绑定 + 委托 + 状态码」。</p>
 *
 * <p>⭐ <b>为什么必须换调度器</b>（同日补，评估发现的 P1-1）：{@code requireRuleAccess} 与
 * {@code getByTaskCode} 都是 MyBatis（JDBC 阻塞），而 WebFlux 的 controller 方法跑在
 * Netty event loop 上——一条慢 SQL 会卡死同线程上的所有连接。搬到 Runner 之前这处
 * <b>本来就没切</b>（既有缺陷，不是本次引入），但薄壳成型后修的位置才清楚：
 * 阻塞段在这里 {@code subscribeOn}，controller 不必知道线程模型。
 * 口径与同包的轮询节拍器（{@code @Scheduled} 那个）、{@code GatewayControllerSupport} 一致：
 * <b>凡是会阻塞的段一律离开 event loop</b>，池满时排队而非就地执行。</p>
 *
 * <p>⚠ <b>异常语义随之变化</b>：首行「不是 Bearer」仍<b>同步</b>抛出（不碰库，无需调度）；
 * 而 {@code requireRuleAccess} 的 401/400 现在是<b>订阅后</b>的 onError 信号。
 * 对外结果不变（{@code GatewayExceptionHandler} 同样接住，仍是 401/400），
 * 但「调用 lookup 时就抛」变成了「block/subscribe 时才抛」——调用方要按后者理解。</p>
 *
 * <p><b>返回 {@code Mono.empty()} 表示「404」</b>：句柄不存在、归属不符、上下文缺失，
 * 三种情况对外是同一个结果，所以 Runner 只表达「没有这个任务」，由 controller 决定那是 404。
 * 反过来让 Runner 直接返回 {@code ResponseEntity} 就是把 HTTP 关切搬回业务层——
 * 这个项目的先例是 {@code GatewayRequestHandler} 返回 {@code Mono<Object>}，
 * {@code GatewayControllerSupport} 再按类型决定 200/202。</p>
 *
 * <p><b>鉴权走 {@code RoutingResolver#requireRuleAccess}，与主链路同一份实现</b>：
 * 只看 {@code Authorization} 是不是 {@code Bearer xxx} 是不够的——那样任何字符串都能查，
 * 任务句柄一泄露就是「谁都能读到别人的任务」。</p>
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
     *
     * <p><b>顺序是「先验 key，再查任务」</b>：凭证不对直接 401，不进库——
     * 由 {@code AsyncTaskLookupRunnerTest#unauthorizedKeyIsRejected} 守着（断言 {@code lookups == 0}）。</p>
     *
     * @param authorization 客户端原始 {@code Authorization} 头（含 {@code Bearer } 前缀）
     * @param rule          客户端声明的规则码，必须与任务归属一致
     * @param taskCode      任务句柄（路径参数）
     * @return 命中时是响应体；查不到 / 不属于该规则时是空 {@link Mono}（调用方映射成 404）
     * @throws GatewayAuthException 凭据缺失或无效（401）；⚠ {@code requireRuleAccess} 抛的那类
     *                              以 onError 形式在订阅后到达，见类注释
     */
    public Mono<AsyncTaskQueryResponse> lookup(String authorization, String rule, String taskCode) {
        if (!StringUtils.startsWith(authorization, AuthConstants.BEARER_PREFIX)) {
            throw new GatewayAuthException("Invalid consumer API key");
        }
        // supplier 返回 null → 空 Mono（Reactor 契约），正好表达「查不到」，不必再造 Optional
        return MdcContextSupport.onBlockingScheduler(
                Mono.fromSupplier(() -> lookupBlocking(authorization, rule, taskCode)),
                blockingScheduler);
    }

    /**
     * 阻塞段：鉴权（查库）→ 查任务（查库）→ 归属比对 → 投影。
     *
     * @return 命中时是响应体；查不到返回 {@code null}（由上层翻译成空 {@code Mono}）
     */
    private AsyncTaskQueryResponse lookupBlocking(String authorization, String rule, String taskCode) {
        routingResolver.requireRuleAccess(authorization.substring(AuthConstants.BEARER_PREFIX.length()), rule);
        AsyncTaskDto task = StringUtils.isBlank(taskCode) ? null : taskService.getByTaskCode(taskCode.trim());
        if (task == null || !ruleMatches(task, rule)) {
            return null;
        }
        return toResponse(task);
    }

    /**
     * 规则归属校验：任务归属的路由规则在 {@code context_json} 里，解析后比对，
     * 不做字符串包含匹配（序列化格式变了就会静默失配）。上下文缺失时按「不匹配」处理 ——
     * 宁可查不到，不可越权可见。
     */
    private boolean ruleMatches(AsyncTaskDto task, String rule) {
        if (StringUtils.isBlank(task.contextJson()) || StringUtils.isBlank(rule)) {
            return false;
        }
        AsyncTaskContext context = JsonKit.fromJson(task.contextJson(), AsyncTaskContext.class);
        return context != null && StringUtils.equals(rule.trim(), context.ruleCode());
    }

    private static AsyncTaskQueryResponse toResponse(AsyncTaskDto task) {
        return new AsyncTaskQueryResponse(task.taskCode(),
                displayStatus(task.status()),
                task.submittedAt(),
                task.completedAt(),
                task.resultExpiresAt(),
                resultOf(task),
                failureOf(task));
    }

    /**
     * 产物反序列化后回填：直接塞 {@code resultJson} 原文会变成「一个 JSON 字符串」，
     * 客户端还得再解一次。解析不出来（理论上不该发生）时回 null 而不是让查询失败 ——
     * 任务状态本身是准确的，产物缺一块比拿不到状态好。
     */
    private static Map<String, Object> resultOf(AsyncTaskDto task) {
        if (StringUtils.isBlank(task.resultJson())) {
            return null;
        }
        return JsonKit.fromJson(task.resultJson(), new TypeReference<Map<String, Object>>() {
        });
    }

    private static AsyncTaskFailureResponse failureOf(AsyncTaskDto task) {
        if (StringUtils.isBlank(task.errorCode())) {
            return null;
        }
        return AsyncTaskFailureResponse.of(task.errorCode(), task.errorMessage());
    }

    /**
     * 对外状态沿用业界习惯：POLLING 归为 processing，PENDING 为 queued。
     *
     * <p>⭐ <b>对外协议与落库取值是两件事</b>：库里存 0~5 的码，客户端看到的是
     * {@code queued/processing/completed/failed}。所以把 status 改成整型<b>没有</b>改动任何对外契约——
     * 这里由枚举 switch 产生字符串，而不是把 DB 值直接透出去。</p>
     *
     * <p>无法识别的码（理论上不该出现，DB 有 CHECK 拦）落成 {@code unknown}：
     * 宁可让客户端看到一个陌生值，也不要把内部码 {@code 3} 当成状态文本发出去。</p>
     */
    private static String displayStatus(Integer status) {
        AsyncTaskStatusEnum parsed = AsyncTaskStatusEnum.fromCode(status);
        if (parsed == null) {
            return "unknown";
        }
        return switch (parsed) {
            case PENDING -> "queued";
            case POLLING -> "processing";
            case SUCCEEDED -> "completed";
            case FAILED, EXPIRED, CANCELLED -> "failed";
        };
    }
}
