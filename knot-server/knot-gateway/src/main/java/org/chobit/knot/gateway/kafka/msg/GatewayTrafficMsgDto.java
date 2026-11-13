package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.common.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.kafka.CallTraceContext;
import org.chobit.knot.gateway.model.GatewayRoutingContext;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.adapter.usage.KnotExtendPayload;
import org.chobit.knot.adapter.usage.ModelUsage;
import org.chobit.knot.gateway.runtime.GatewayExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * 网关流量事件（单条消息）：一次客户端请求从入口到终态的完整事实，在请求终态发出（非流式记账后、流式响应写完的 finally、异常路径的 catch）；入口即拒也发一条，{@code routing} 与 {@code hit} 为 null、{@code error.stage} 为 {@code header_validate}。
 * 携带路由维度快照（业务码与 username，无主键 id / secretKey）与处置结果（真正请求的模型 + 结果 / 用量 / 计费 / 错误 / 上游调用明细），由 {@code traceId} 自关联；{@code hit} 取实际调用上游的候选，池命中带 {@code poolCode}。
 * 用量三视图单向派生：{@code upstream_usage}（原文透传）→ {@code usage}（归一化 + 计费属性，分档键随 {@code usage.attributes} 给出）→ {@code billing}（按规则算价）；{@code trace_id} 为可继承的追踪 id，{@code request_id} 为网关入口生成（32 位 hex），{@code attempts[].upstream_request_id} 为上游返回。
 * 异步请求发 1 + N 条事件（{@code create} 1 次 + {@code query} N 次），靠 {@code task_code} 归组、{@code task_phase} / {@code task_terminal} 区分；数仓聚合：客户端请求数取 {@code where task_phase is null or task_phase = 'submit'}，计费金额与失败率取 {@code where task_phase is null or task_terminal = true}，上游调用数不加条件。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GatewayTrafficMsgDto(String schema,
                                   String traceId,
                                   String spanId,
                                   String requestId,
                                   String nodeId,
                                   String receivedAt,
                                   String finishedAt,
                                   long durationMs,
                                   String protocol,
                                   Boolean stream,
                                   String stickyKey,
                                   RoutingSnapshot routing,
                                   String status,
                                   int httpStatus,
                                   Long ttftMs,
                                   HitSnapshot hit,
                                   List<AttemptMsgDto> attempts,
                                   int retryTotal,
                                   int failoverTotal,
                                   int ejectedSkipped,
                                   boolean failOpen,
                                   Map<String, Object> upstreamUsage,
                                   ModelUsage usage,
                                   BillingViewMsgDto billing,
                                   String taskCode,
                                   String taskPhase,
                                   Boolean taskTerminal,
                                   Integer taskQuerySeq,
                                   Integer taskRetryRound,
                                   ErrorMsgDto error) implements Msg {

    /** 消息契约版本号。 */
    private static final String SCHEMA = "knot.gateway.traffic/1.1";

    /**
     * 异步任务身份：本次事件属于哪一笔任务、哪个阶段；经 context 属性（{@link #ATTRIBUTE}）传递。
     *
     * @param phase    {@link #PHASE_SUBMIT}（create 调用）或 {@link #PHASE_QUERY}（查询上游任务状态）
     * @param terminal 该阶段是否已落终态；{@code submit} 恒 {@code false}
     * @param querySeq 第几次查询，从 1 开始；{@code submit} 为 {@code null}
     * @param retryRound 重试轮次，0 = 从未被管理端重试；进去重键，数仓按 {@code (task_code, task_phase, task_query_seq)} 去重时须带上 {@code task_retry_round}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TaskTrace(String taskCode, String phase, boolean terminal, Integer querySeq,
                            Integer retryRound) {

        /** context 属性名：由提交路径写入，{@link GatewayTrafficMsgDto#of} 读取 */
        public static final String ATTRIBUTE = "asyncTaskTrace";

        public static final String PHASE_SUBMIT = "submit";
        public static final String PHASE_QUERY = "query";

        /** 提交阶段：上游只回了 task_id，钱还没发生。轮次恒 0（还没被重试过） */
        public static TaskTrace submit(String taskCode) {
            return new TaskTrace(taskCode, PHASE_SUBMIT, false, null, 0);
        }

        /** 查询阶段：{@code terminal} 表示这次查询是否拿到的就是终态 */
        public static TaskTrace query(String taskCode, int retryRound, int querySeq, boolean terminal) {
            return new TaskTrace(taskCode, PHASE_QUERY, terminal, querySeq, retryRound);
        }
    }

    /**
     * 路由维度快照：供多维统计直接切片。
     *
     * @param targetType     主目标类型（MODEL / MODEL_POOL）。池展开后候选的 targetType 已被路由
     *                       统一改写为 MODEL，这里按首个候选是否带来源池 code 还原规则维度的目标类型
     * @param candidateCount 候选数量；>1 才有 failover / 负载均衡的统计意义
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RoutingSnapshot(String ruleCode,
                                  String ruleName,
                                  String appScenario,
                                  String consumerCode,
                                  String consumerName,
                                  String appCode,
                                  String appName,
                                  String deptCode,
                                  String deptName,
                                  String consumerUsername,
                                  String appOwnerUsername,
                                  Boolean returnUsageDetail,
                                  String targetType,
                                  int candidateCount) {
    }

    /**
     * 实际承接本次请求的上游侧标识（真正请求的模型）。
     *
     * @param modelCode           KB 侧模型业务码（也是响应头 X-Knot-Model 的值）
     * @param upstreamModelCode   实际写入请求体 model 的上游标识（可能与前者不同）
     * @param providerAccountCode 供应商账户业务码
     * @param poolCode            来源模型池业务码；规则直挂模型为 null（池内命中时归因到池）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record HitSnapshot(String modelCode,
                              String upstreamModelCode,
                              String providerAccountCode,
                              String poolCode,
                              String upstreamUrl) {
    }

    /**
     * 由请求上下文与本次调用产物构建终态消息。
     *
     * @param exchange 请求载体，用于读取 {@code stream} 开关（取不到就是 null）
     * @param usage    本次调用的用量账目；失败路径为 {@code null}
     * @param ttftMs   首 token 耗时（毫秒）；{@code null} 表示没有成功首 token
     * @param error    终止本次请求的异常；成功路径为 {@code null}
     */
    public static GatewayTrafficMsgDto of(GatewayRequestContext context,
                                         GatewayExchange exchange,
                                         UsageAccounting usage,
                                         Long ttftMs,
                                         Throwable error) {
        long receivedAtMillis = context == null ? 0L : context.receivedAtMillis();
        long finishedAtMillis = System.currentTimeMillis();
        ErrorMsgDto errorRecord = ErrorMsgDto.of(error);
        KnotExtendPayload payload = usage == null ? null : KnotExtendPayload.of(usage);
        CallTraceContext trace = context == null ? null : context.attribute(CallTraceContext.ATTRIBUTE);
        TaskTrace task = taskTraceOf(context);
        return new GatewayTrafficMsgDto(
            SCHEMA,
            context == null ? null : context.traceId(),
            context == null ? null : context.spanId(),
            context == null ? null : context.requestId(),
            Msg.NODE_ID,
            iso(receivedAtMillis),
            iso(finishedAtMillis),
            Math.max(0L, finishedAtMillis - receivedAtMillis),
            context == null || context.protocol() == null ? null : context.protocol().code(),
            streamOf(exchange),
            context == null ? null : context.stickyKey(),
            routingSnapshot(context == null ? null : context.routing()),
            statusOf(errorRecord),
            httpStatusOf(errorRecord),
            ttftMs,
            hitSnapshot(context),
            trace == null ? List.of() : trace.records(),
            trace == null ? 0 : trace.retryTotal(),
            trace == null ? 0 : trace.failoverTotal(),
            trace == null ? 0 : trace.ejectedSkipped(),
            trace != null && trace.failOpenApplied(),
            upstreamUsageOf(usage),
            payload == null ? null : payload.usage(),
            billingView(usage),
            task == null ? null : task.taskCode(),
            task == null ? null : task.phase(),
            task == null ? null : task.terminal(),
            task == null ? null : task.querySeq(),
            task == null ? null : task.retryRound(),
            errorRecord);
    }

    /**
     * 异步任务身份；同步请求为 {@code null}，五个 {@code task_*} 字段随 {@code NON_NULL} 整段省略，不带 {@code task_terminal}。
     * 数仓口径按 {@code task_phase is null or ...} 过滤同步事件。
     */
    private static TaskTrace taskTraceOf(GatewayRequestContext context) {
        return context == null ? null : context.attribute(TaskTrace.ATTRIBUTE);
    }

    /**
     * 上游 {@code usage} 原文；空对象归一成 {@code null}，让 {@code NON_NULL} 整段省略——
     * 数仓侧「字段缺席」与「上游没报」应是同一件事，不留空壳对象。
     */
    private static Map<String, Object> upstreamUsageOf(UsageAccounting usage) {
        if (usage == null) {
            return null;
        }
        Map<String, Object> rawUsage = usage.rawUsage();
        return rawUsage == null || rawUsage.isEmpty() ? null : rawUsage;
    }

    /**
     * 真正请求的模型 = 本次请求实际调用上游的那个候选（hit target）。
     * 候选调用成功后由 {@link GatewayRequestHandler} 写入上下文属性
     * {@link GatewayRequestHandler#HIT_TARGET_ATTRIBUTE}；取不到即没有候选真正承接
     * （路由解析后的拒绝 / 上游全失败路径），{@code hit} 为 null。
     */
    private static HitSnapshot hitSnapshot(GatewayRequestContext context) {
        if (context == null) {
            return null;
        }
        RoutingRuleTargetDto hit = context.attribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE);
        if (hit == null) {
            return null;
        }
        String upstreamUrl = context == null ? null
                : (String) context.attribute(GatewayRequestHandler.HIT_TARGET_URL_ATTRIBUTE);
        return new HitSnapshot(hit.targetCode(), hit.upstreamModelCode(), hit.providerAccountCode(),
                hit.poolCode(), upstreamUrl);
    }

    private static RoutingSnapshot routingSnapshot(ResolvedRouting routing) {
        if (routing == null) {
            return null;
        }
        GatewayRoutingContext info = routing.routingContext();
        GatewayRoutingContext.RuleSnapshot rule = info == null ? null : info.rule();
        GatewayRoutingContext.ConsumerSnapshot consumer = info == null ? null : info.consumer();
        GatewayRoutingContext.AppSnapshot app = info == null ? null : info.app();
        GatewayRoutingContext.DepartmentSnapshot dept = info == null ? null : info.department();
        GatewayRoutingContext.UserSnapshot consumerUser = info == null ? null : info.consumerUser();
        GatewayRoutingContext.UserSnapshot owner = info == null ? null : info.appOwner();
        List<RoutingRuleTargetDto> candidates = routing.candidateModels();
        RoutingRuleTargetDto first =
                candidates == null || candidates.isEmpty() ? null : candidates.get(0);
        // 池展开后候选 targetType 统一改写为 MODEL，规则维度的目标类型按首个候选
        // 是否带来源池 code 还原：带池 code 即主目标为模型池
        String targetType = first == null ? null
                : (first.poolCode() != null
                ? RouteTargetTypeEnum.MODEL_POOL.code()
                : RouteTargetTypeEnum.MODEL.code());
        return new RoutingSnapshot(
            rule == null ? null : rule.code(),
            rule == null ? null : rule.name(),
            rule == null ? null : rule.appScenario(),
            consumer == null ? null : consumer.code(),
            consumer == null ? null : consumer.name(),
            app == null ? null : app.appId(),
            app == null ? null : app.name(),
            dept == null ? null : dept.deptCode(),
            dept == null ? null : dept.name(),
            consumerUser == null ? null : consumerUser.username(),
            owner == null ? null : owner.username(),
            consumer == null ? null : consumer.returnUsageDetail(),
            targetType,
            candidates == null ? 0 : candidates.size());
    }

    /**
     * 计费视图；归因字段（规则业务码 / 模式 / 方案）由归一化结果直接携带，
     * 见 {@link BillingViewMsgDto#of(NormalizedUsage)}。
     */
    private static BillingViewMsgDto billingView(UsageAccounting usage) {
        if (usage == null) {
            return null;
        }
        return BillingViewMsgDto.of(usage.normalized());
    }

    private static String statusOf(ErrorMsgDto error) {
        if (error == null) {
            return "success";
        }
        GatewayErrorTypeEnum type = GatewayErrorTypeEnum.fromCode(error.type());
        if (type == null) {
            // 未知 type 不炸（数仓链路宁可少一列语义，也不能因为一个新码丢掉整条事件），落最保守的 gateway_error
            return "gateway_error";
        }
        // 穷举枚举而不是手写字符串：新增错误类型时这里会编译失败，强制作者决定它的 status
        return switch (type) {
            case RATE_LIMIT_ERROR -> "rate_limited";
            case QUOTA_ERROR -> "quota_exceeded";
            case UPSTREAM_ERROR -> "upstream_error";
            case TIMEOUT_ERROR -> "timeout";
            case AUTH_ERROR, INVALID_REQUEST_ERROR, MODERATION_ERROR, STORAGE_UNAVAILABLE, INTERNAL_ERROR
                    -> "gateway_error";
        };
    }

    private static int httpStatusOf(ErrorMsgDto error) {
        return error == null ? 200 : error.httpStatus();
    }

    private static Boolean streamOf(GatewayExchange exchange) {
        if (exchange == null || exchange.requestBody() == null) {
            return null;
        }
        Object stream = exchange.requestBody().get(AiPayloadFields.STREAM);
        return stream instanceof Boolean value ? value : null;
    }

    private static String iso(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .toOffsetDateTime()
            .toString();
    }
}
