package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.kafka.CallTraceContext;
import org.chobit.knot.gateway.model.GatewayRoutingInfo;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.model.usage.KnotExtendPayload;
import org.chobit.knot.gateway.model.usage.ModelUsage;
import org.chobit.knot.gateway.runtime.GatewayExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

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
 * <p><b>三个 id 语义分明，不可互相替代</b>（2026-10-23）：</p>
 * <ul>
 *   <li>{@code trace_id} —— W3C 追踪 id，可能<b>继承自调用方</b>的 traceparent，不保证是我方发的；</li>
 *   <li>{@code request_id} —— 网关<b>入口生成</b>的请求编号（32 位 hex），已以 {@code X-Request-Id}
 *       透传给上游，是对账时的「我方 id」；</li>
 *   <li>{@code attempts[].upstream_request_id} —— 上游<b>返回</b>的编号，是「对方 id」。</li>
 * </ul>
 * <p>双向对账的检索路径：拿 {@code request_id} 去供应商侧查（他们日志里有这个头），
 * 拿 {@code upstream_request_id} 去我们数仓查（它来自对方响应头）。</p>
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
                                   ModelUsage usage,
                                   BillingViewMsgDto billing,
                                   ErrorMsgDto error) implements Msg {

    private static final String SCHEMA = "knot.gateway.traffic/1.0";

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
            payload == null ? null : payload.usage(),
            billingView(usage),
            errorRecord);
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
        GatewayRoutingInfo info = routing.routingInfo();
        GatewayRoutingInfo.RuleInfo rule = info == null ? null : info.rule();
        GatewayRoutingInfo.ConsumerInfo consumer = info == null ? null : info.consumer();
        GatewayRoutingInfo.AppInfo app = info == null ? null : info.app();
        GatewayRoutingInfo.DepartmentInfo dept = info == null ? null : info.department();
        GatewayRoutingInfo.UserInfo consumerUser = info == null ? null : info.consumerUser();
        GatewayRoutingInfo.UserInfo owner = info == null ? null : info.appOwner();
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
