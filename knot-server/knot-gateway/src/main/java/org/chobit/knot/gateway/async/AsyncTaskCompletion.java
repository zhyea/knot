package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.kafka.KafkaPublisher;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto.TaskTrace;
import org.chobit.knot.gateway.model.GatewayRoutingContext;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.runtime.GatewayExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.TrafficUsage;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.gateway.upstream.UpstreamProxyClient;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * 异步任务终态的计费与事件：把「查询响应体」交给既有提取与计费管线。
 * 用量由绑定上的 {@code usage_extractor} 决定，金额由对应 {@code BillingModeCalculator} 决定，本类不含计价逻辑。
 */
@Slf4j
@Component
public class AsyncTaskCompletion implements AsyncTaskSettlementPort {

    private final UsageExtractorRegistry usageExtractorRegistry;
    private final UpstreamProxyClient proxyClient;
    private final GatewayConfigCacheService dataService;
    private final KafkaPublisher eventPublisher;
    private final String trafficTopic;
    private final GatewayTrafficGuard trafficGuard;

    public AsyncTaskCompletion(UsageExtractorRegistry usageExtractorRegistry,
                              UpstreamProxyClient proxyClient,
                              GatewayConfigCacheService dataService,
                              @Nullable KafkaPublisher eventPublisher,
                              @Value("${kafka.topic.traffic}") String trafficTopic,
                              @Nullable GatewayTrafficGuard trafficGuard) {
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.proxyClient = proxyClient;
        this.dataService = dataService;
        this.eventPublisher = eventPublisher;
        this.trafficTopic = trafficTopic;
        this.trafficGuard = trafficGuard;
    }

    /**
     * 用查询响应体跑一遍既有的提取与计费。
     * 请求体取自任务快照（客户端原始体）；转换后的上游体只用于发请求，从不回灌给提取器。
     * 查询响应体按两份用：算账吃按档案 {@code usage_path} 加工后的 body，证据吃原文，两者的来源不许互换。
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
        // 只加工「交给提取器的输入」；证据另传原文，与落库的 terminal_body 同源
        String evidenceBody = queryBody;
        queryBody = queryBodyForUsage(queryBody, task.pollSpecJson());
        return usageExtractorRegistry.extract(queryBody, evidenceBody, upstreamContext);
    }

    /**
     * 把查询响应里「按档案 usagePath 取到的 usage」补到顶层，供既有提取器读取。
     * 加工的是交给提取器的输入，不是落库证据：{@code AsyncTaskRunner#finish} 落 {@code terminal_body} 的仍是 {@code polling.rawBody()} 原文；
     * 配了 {@code usage_path} 时以配置为准，覆盖上游顶层同名 {@code usage} 键。
     * 包级可见，仅供单测验证「加工后的 body 顶层 usage 带 cost」，不对外暴露。
     */
    static String queryBodyForUsage(String queryBody, String pollSpecJson) {
        Map<String, Object> body = JsonKit.fromJson(queryBody,
                new TypeReference<Map<String, Object>>() {
                });
        AsyncTaskSpec spec = JsonKit.fromJson(pollSpecJson, AsyncTaskSpec.class);
        Map<String, Object> usage = AsyncTaskSpecRules.usageOf(body, spec);
        if (body == null || usage == null || usage.isEmpty()) {
            return queryBody;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(body);
        normalized.put(AiPayloadFields.USAGE, usage);
        return JsonKit.toJson(normalized);
    }

    /**
     * 终态成功后的限额记账：只使用任务快照里预存的资源主键，不重新解析 API key。
     * 提交阶段跳过记账，这里补上真实用量；终态 CAS 保证一条任务只到这里一次。
     * 旧的上下文 JSON 没有资源主键时，对应维度跳过。
     */
    @Override
    public void recordUsage(AsyncTaskDto task, AsyncTaskContext context, UsageAccounting usage) {
        if (trafficGuard == null || usage == null) {
            return;
        }
        GatewayTrafficGuard.TrafficCheckContext checkContext = trafficGuard.newContext();
        trafficGuard.record(routingOf(context), targetOf(context), TrafficUsage.of(usage), checkContext);
    }

    /**
     * 发布一次查询的流量事件（中间过程，不是终态）；每次查询各发一条。
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
     * 路由维度（app / consumer / dept）为 {@code null}。
     * 与 {@link #publishQuery} 同一条事件流，以 {@code task_terminal=true} 区分，计费与失败率统计都以该字段为准。
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
     * 重试轮次：库里的 {@code retry_round} 可能为 null，归一成 0。
     * 轮次只取行上的值，不做「第一次查询」之类的推断。
     */
    private static int roundOf(AsyncTaskDto task) {
        return task.retryRound() == null ? 0 : task.retryRound();
    }

    /**
     * 重建记账所需的资源坐标；展示字段（应用名 / 消费者名 / 部门）不在上下文里，故留空。
     */
    private static ResolvedRouting routingOf(AsyncTaskContext context) {
        GatewayRoutingContext routingContext = new GatewayRoutingContext(
                null,
                new GatewayRoutingContext.ConsumerSnapshot(context.consumerId(), null, null, null, false),
                new GatewayRoutingContext.AppSnapshot(context.appId(), null, null, null),
                null, null, null);
        return new ResolvedRouting(context.routingRuleId(), context.ruleCode(), context.consumerId(),
                null, false, List.of(), routingContext, null, null);
    }

    private static RoutingRuleTargetDto targetOf(AsyncTaskContext context) {
        return new RoutingRuleTargetDto(null, context.targetModelId(), context.modelCode(),
                context.upstreamModelCode(), null, null, context.providerAccountCode(),
                0, false, context.poolCode());
    }

    /**
     * 查询与终态两个入口共用的装配逻辑；路由维度与 hit 快照的重建只在这一处。
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
     * 构造一个可下发的异常：中间查询的失败可能连上游响应都没有（超时 / 连接重置），事件里必须带 {@code error.stage} 供数仓归因。
     * 调用方传「码 + 话术」，不传现成的异常。
     */
    private static Throwable errorOf(String errorCode, String errorMessage) {
        if (errorCode == null && errorMessage == null) {
            return null;
        }
        String code = errorCode == null ? "ASYNC_TASK_FAILED" : errorCode;
        return new GatewayUpstreamException(errorMessage == null ? code : errorMessage, code);
    }
}
