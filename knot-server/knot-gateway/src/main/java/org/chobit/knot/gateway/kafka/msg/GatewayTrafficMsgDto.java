package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.common.constants.AiPayloadFields;
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
 * <b>网关流量事件（单条消息）</b>：一次客户端请求从入口到终态的完整事实。
 *
 * <p>2026-10-18 robin 拍板合并——原「请求层 + 响应层」两条 Kafka 消息收敛为<b>一条</b>：
 * 数仓是离线批处理，不需要请求层早期发出的「实时流量入口」信号（流式长连接期间入口维度
 * 延迟到终态才可见可接受），合并后能少一次发布、消除两层乱序、入口即拒不再需要 left join 识别。</p>
 *
 * <p>发送时机是<b>请求终态</b>：非流式在用量记账后发；流式在响应体写完的 finally 发；
 * 异常路径（含入口即拒）在 catch 发。一条消息同时携带「谁在调、走哪条规则、落到哪个应用 / 部门 /
 * 用户」（路由维度快照）与「真正请求的模型 + 结果 / 用量 / 计费 / 错误 / 上游调用明细」（处置结果），
 * 由 {@code traceId} 自关联，不再需要两层消息的关联与乱序修正。</p>
 *
 * <p>入口即拒（鉴权 / Rule 头校验失败）同样发一条：{@code routing} 为 null、{@code hit} 为 null、
 * {@code error.stage} 为 {@code header_validate}——这是设计口径，一条记录即可表达，无需 left join。</p>
 *
 * <p>维度<b>全部是业务码与 username，没有主键 id、没有 secretKey</b>：业务码跨环境稳定，
 * 主键 id 换个部署就变，落到数仓无法归因。</p>
 *
 * <p>{@code hit}（真正请求的模型）取自候选循环落定后实际调用上游的那个候选（hit target，
 * 与旧请求层模型三字段同口径），不是候选链首位、也不是备选清单；池命中时带 {@code poolCode}
 * 供数仓归因到具体模型池。</p>
 *
 * <p><b>用量三视图，单向派生</b>（2026-10-27）：{@code upstream_usage}（上游 {@code usage} 原文，
 * 只透传不加工）→ {@code usage}（归一化用量 + 本次请求的计费属性）→ {@code billing}（本网关按规则算出的价格）。
 * 三者并列下发是因为下游要能<b>自行复算</b>（转卖 token、成本分摊、毛利核算）：
 * 归一化口径若有争议，靠 {@code upstream_usage} 可回溯；网关<b>不下发计费规则内容</b>，
 * 所以复算所需的分档键（分辨率 / 画幅 / 模式 / 有声…）必须随 {@code usage.attributes} 一起给出。</p>
 *
 * <p><b>三个 id 语义分明，不可互相替代</b>（2026-10-23）：</p>
 * <ul>
 *   <li>{@code trace_id} —— W3C 追踪 id，可能<b>继承自调用方</b>的 traceparent，不保证是我方发的；</li>
 *   <li>{@code request_id} —— 网关<b>入口生成</b>的请求编号（32 位 hex），已以 {@code X-Request-Id}
 *       透传给上游，是对账时的「我方 id」；</li>
 *   <li>{@code attempts[].upstream_request_id} —— 上游<b>返回</b>的编号，是「对方 id」。</li>
 * </ul>
 * <p>双向对账的检索路径：拿 {@code request_id} 去供应商侧查（他们日志里有这个头），
 * 拿 {@code upstream_request_id} 去我们数仓查（它来自对方响应头）。</p>
 *
 * <p><b>异步任务是「1 次提交 + N 次查询」，不是「1 次请求」</b>（2026-10-28 改口径）：
 * 一次异步调用的真实上游开销是 {@code create} 一次 + {@code query} N 次（N 由产物耗时与轮询间隔决定，
 * 视频任务可达数百）。所以这类请求<b>发 1 + N 条事件</b>，靠 {@code task_code} 归到同一笔任务，
 * 用 {@code task_phase}（{@code submit} / {@code query}）与 {@code task_terminal} 区分每条的身份。</p>
 *
 * <p>⚠ <b>数仓聚合必须带口径，否则每条都会算错</b>：</p>
 * <ul>
 *   <li>客户端请求数 —— {@code where task_phase is null or task_phase = 'submit'}</li>
 *   <li>上游调用数 —— 不加条件（同步 1 条 = 1 次调用；异步 1 + N 条 = 1 + N 次调用）</li>
 *   <li>计费金额 —— {@code where task_phase is null or task_terminal = true}（同步事件不带
 *       {@code task_phase}，天然全计）</li>
 *   <li>失败率 —— <b>必须</b>加 {@code where task_phase is null or task_terminal = true}：
 *       中间查询的失败（上游 5xx 后重试成功）会各自发一条带 {@code error} 的事件，
 *       不过滤就会把「重试 5 次后成功」算成 5 次失败</li>
 * </ul>
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
                                   ErrorMsgDto error) implements Msg {

    /**
     * 契约版本。
     *
     * <p>⭐ <b>1.0 → 1.1 不是加字段，是改基数</b>（2026-10-28）：异步请求从「1 次请求 1 条事件」
     * 变成「1 次提交 + N 次查询 = 1 + N 条事件」。字段层面完全向后兼容，但
     * <b>任何按「一条消息 = 一次请求」写的消费逻辑都会静默多算</b>——这正是 {@code schema}
     * 存在的意义，所以必须升版本，而不是沿用 1.0 只加字段。</p>
     */
    private static final String SCHEMA = "knot.gateway.traffic/1.1";

    /**
     * 异步任务身份：本次事件属于<b>哪一笔任务、哪个阶段</b>。
     *
     * <p>走 context 属性传递（{@link #ATTRIBUTE}）而不是给 {@link #of} 加参数：{@code of} 有三个调用点，
     * 加参数要么改三处签名，要么让其中两处传 {@code null}。属性方案下模板层<b>完全不需要知道异步的存在</b>——
     * 它照常发事件，事件自己带着身份走。这是与「抑制创建事件」路线的根本差别：
     * 抑制丢掉了「提交」这次上游调用，而它确实发生过。</p>
     *
     * @param phase    {@link #PHASE_SUBMIT}（create 调用）或 {@link #PHASE_QUERY}（查询上游任务状态）
     * @param terminal 该阶段是否已落终态；{@code submit} 恒 {@code false}
     * @param querySeq 第几次查询，从 1 开始；{@code submit} 为 {@code null}。
     *                 有它才能算出「第几次查询才拿到产物」，是异步任务延迟分布的唯一来源
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TaskTrace(String taskCode, String phase, boolean terminal, Integer querySeq) {

        /** context 属性名：由提交路径写入，{@link GatewayTrafficMsgDto#of} 读取 */
        public static final String ATTRIBUTE = "asyncTaskTrace";

        public static final String PHASE_SUBMIT = "submit";
        public static final String PHASE_QUERY = "query";

        /** 提交阶段：上游只回了 task_id，钱还没发生 */
        public static TaskTrace submit(String taskCode) {
            return new TaskTrace(taskCode, PHASE_SUBMIT, false, null);
        }

        /** 查询阶段：{@code terminal} 表示这次查询是否拿到的就是终态 */
        public static TaskTrace query(String taskCode, int querySeq, boolean terminal) {
            return new TaskTrace(taskCode, PHASE_QUERY, terminal, querySeq);
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
            errorRecord);
    }

    /**
     * 异步任务身份；同步请求为 {@code null}，四个 {@code task_*} 字段随 {@code NON_NULL} 整段省略。
     *
     * <p>⚠ 同步事件<b>不带</b> {@code task_terminal}，所以数仓口径写的是
     * {@code task_phase is null or ...} 而不是 {@code task_terminal = true}——
     * 后者会把全部同步请求过滤掉。这条口径在类 javadoc 里也写了一遍，两处必须一致。</p>
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
     *
     * <p>候选调用成功后由 {@link GatewayRequestHandler} 写入上下文属性
     * {@link GatewayRequestHandler#HIT_TARGET_ATTRIBUTE}；取不到即没有候选真正承接
     * （路由解析后的拒绝 / 上游全失败路径），{@code hit} 为 null。</p>
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
     * 计费视图；规则业务码无法从用量结果反推（{@link NormalizedUsage} 只带 versionCode），
     * 归因走 {@code billingVersion} 与 {@code kb_billing_rules.version_code} 关联。
     */
    private static BillingViewMsgDto billingView(UsageAccounting usage) {
        if (usage == null) {
            return null;
        }
        return BillingViewMsgDto.of(usage.normalized(), null, null);
    }

    private static String statusOf(ErrorMsgDto error) {
        if (error == null) {
            return "success";
        }
        return switch (error.type()) {
            case "rate_limit_error" -> "rate_limited";
            case "quota_error" -> "quota_exceeded";
            case "upstream_error" -> "upstream_error";
            case "timeout_error" -> "timeout";
            default -> "gateway_error";
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
