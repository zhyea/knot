package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.kafka.KafkaPublisher;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto.TaskTrace;
import org.chobit.knot.gateway.runtime.GatewayExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.gateway.upstream.UpstreamProxyClient;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 异步任务终态的计费与事件：把「查询响应体」交给<b>既有</b>提取与计费管线。
 *
 * <p>⭐ 本类没有任何计价逻辑：用量仍由绑定上的 {@code usage_extractor} 决定、
 * 金额仍由对应 {@code BillingModeCalculator} 决定。异步只是把触发点从请求线程挪到轮询线程、
 * 把输入体从创建响应换成查询响应——<b>计费口径一行不改</b>。
 * 在轮询侧另写一份字段解析是本项目明令禁止的漂移源。</p>
 */
@Slf4j
@Component
public class AsyncTaskCompletion implements AsyncTaskSettlementPort {

    private final UsageExtractorRegistry usageExtractorRegistry;
    private final UpstreamProxyClient proxyClient;
    private final GatewayConfigCacheService dataService;
    private final KafkaPublisher eventPublisher;
    private final String trafficTopic;

    public AsyncTaskCompletion(UsageExtractorRegistry usageExtractorRegistry,
                              UpstreamProxyClient proxyClient,
                              GatewayConfigCacheService dataService,
                              @Nullable KafkaPublisher eventPublisher,
                              @Value("${kafka.topic.traffic}") String trafficTopic) {
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.proxyClient = proxyClient;
        this.dataService = dataService;
        this.eventPublisher = eventPublisher;
        this.trafficTopic = trafficTopic;
    }

    /**
     * 用查询响应体跑一遍既有的提取与计费。
     *
     * <p>请求体取自任务快照（<b>客户端原始体</b>）——提取器读的就是这一份，
     * 与同步链路完全一致（转换后的上游体只用于发请求，从不回灌给提取器）。</p>
     */
    @Override
    public UsageAccounting charge(AsyncTaskDto task, AsyncTaskContext context, String queryBody) {
        ModelConfigVo model = dataService.getModelByCode(context.modelCode());
        if (model == null) {
            log.error("Async task billing skipped: model not found: {}", context.modelCode());
            return null;
        }
        Map<String, Object> requestBody = JsonKit.fromJson(task.requestJson(),
                new TypeReference<Map<String, Object>>() {
                });
        UpstreamRequestContext upstreamContext = proxyClient.buildModelRequestContext(
                requestBody == null ? Map.of() : requestBody,
                MediaType.APPLICATION_JSON,
                model.id(),
                ModelApiProtocolEnum.fromCode(context.protocol()),
                context.traceparent(),
                context.requestId());
        return usageExtractorRegistry.extract(queryBody, upstreamContext);
    }

    /**
     * 发布<b>一次查询</b>的流量事件（中间过程，不是终态）。
     *
     * <p>⭐ 为什么中间查询也要发：一次异步调用在上游是「1 次提交 + N 次查询」，N 可达数百。
     * 只发终态那条等于把 N-1 次上游调用从数仓里抹掉——上游成本算不出、产物延迟分布算不出、
     * 「重试 5 次才成功」会被压平成「1 次成功」。这些事实只能靠每次查询各发一条来保留。</p>
     *
     * @param querySeq  第几次查询（从 1 开始，对应行上的 {@code attempts}）
     * @param errorCode 本次查询是否出错（{@code QUERY_ERROR} 时为错误码；拿到有效响应时为 null）
     */
    @Override
    public void publishQuery(AsyncTaskDto task, AsyncTaskContext context, int querySeq, String errorCode) {
        publish(task, context, TaskTrace.query(task.taskCode(), roundOf(task), querySeq, false),
                null, errorCode, null);
    }

    /**
     * 发布终态流量事件（一条任务一条）。
     *
     * <p>路由维度（app / consumer / dept）在本轮为 {@code null}：重建 {@code ResolvedRouting}
     * 需要 API key 重新解析，而「不把调用方密钥写进任务表」优先。缺口已在方案文档登记。</p>
     *
     * <p>与 {@link #publishQuery} 是<b>同一条事件流</b>里的最后一环：{@code task_terminal=true}
     * 是它唯一与众不同之处，计费与失败率统计都以这一个字段为准。</p>
     *
     * @param error 失败/超时原因；成功路径为 {@code null}
     */
    @Override
    public void publishTerminal(AsyncTaskDto task, AsyncTaskContext context,
                                int querySeq, UsageAccounting usage, String errorCode, String errorMessage) {
        publish(task, context, TaskTrace.query(task.taskCode(), roundOf(task), querySeq, true),
                usage, errorCode, errorMessage);
    }

    /**
     * 重试轮次：库里的 {@code retry_round} 可能是 null（历史行在加列前就已存在，
     * 加列时默认 0，但手工插入的测试数据仍可能为 null）→ 归一成 0。
     *
     * <p>⚠ 不要在这里做「第一次查询」之类的推断：轮次只有一个来源，就是行上的值。</p>
     */
    private static int roundOf(AsyncTaskDto task) {
        return task.retryRound() == null ? 0 : task.retryRound();
    }

    /**
     * 查询与终态两个入口共用的装配逻辑。
     *
     * <p>路由维度与 hit 快照的重建只在<b>这一处</b>：两份实现必然漂移，而漂移的表现是
     * 「终态事件有模型、中间查询事件没有」，数仓 join 时静默丢行。</p>
     */
    private void publish(AsyncTaskDto task, AsyncTaskContext context, TaskTrace trace,
                         UsageAccounting usage, String errorCode, String errorMessage) {
        if (eventPublisher == null) {
            return;
        }
        ModelConfigVo model = dataService.getModelByCode(context.modelCode());
        // ⭐ 必须用 replay 而不是 fromTraceparent：后者会为「本次」生成新 span，
        // 于是同一笔任务的 1 + N 条事件拿到 N 个不同 span_id —— 数仓按 (trace_id, span_id)
        // 聚不到一起，等于白留了 trace。replay 沿用提交那次的 span 与 requestId。
        GatewayTraceContext traceContext =
                GatewayTraceContext.replay(context.traceparent(), context.requestId());
        GatewayRequestContext requestContext = new GatewayRequestContext(
                null, context.ruleCode(), traceContext, ModelApiProtocolEnum.fromCode(context.protocol()),
                context.stickyKey());
        RoutingRuleTargetDto target = new RoutingRuleTargetDto(
                null, model == null ? null : model.id(), context.modelCode(), context.upstreamModelCode(),
                null, null, context.providerAccountCode(), 0, false, context.poolCode());
        requestContext.putAttribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE, target);
        requestContext.putAttribute(GatewayRequestHandler.HIT_TARGET_URL_ATTRIBUTE, context.upstreamUrl());
        requestContext.putAttribute(TaskTrace.ATTRIBUTE, trace);
        Map<String, Object> requestBody = JsonKit.fromJson(task.requestJson(),
                new TypeReference<Map<String, Object>>() {
                });
        GatewayExchange exchange = new GatewayExchange(requestBody, MediaType.APPLICATION_JSON);
        eventPublisher.publish(trafficTopic, GatewayTrafficMsgDto.of(requestContext, exchange, usage, null,
                errorOf(errorCode, errorMessage)));
    }

    /**
     * 构造一个可下发的异常：中间查询的失败可能连上游响应都没有（超时 / 连接重置），
     * 但事件里必须有 {@code error.stage} 才能被数仓归因。
     *
     * <p>调用方传的是「码 + 话术」而不是一个现成的异常：编排层（svc）不该为了发一条事件
     * 去 import 网关的异常类型——那是把两个 app 的类型系统绑在一起。</p>
     */
    private static Throwable errorOf(String errorCode, String errorMessage) {
        if (errorCode == null && errorMessage == null) {
            return null;
        }
        String code = errorCode == null ? "ASYNC_TASK_FAILED" : errorCode;
        return new GatewayUpstreamException(errorMessage == null ? code : errorMessage, code);
    }
}
