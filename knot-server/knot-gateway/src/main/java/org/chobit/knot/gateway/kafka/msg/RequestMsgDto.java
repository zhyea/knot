package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.model.GatewayRoutingInfo;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.runtime.GatewayExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * <b>请求层</b>消息：一次客户端请求的输入与其路由维度快照。
 *
 * <p>发送时机是路由解析成功后——此时「谁在调、走哪条规则、落到哪个应用 / 部门 / 用户」都已确定，
 * 而结果（成败、用量、异常）一个都不知道。结果与只看结果的两个关注点彻底分开，
 * 数仓拿的东西便是「流量入口」和「处置结果」两张独立的事实。</p>
 *
 * <p>维度<b>全部是业务码与 username，没有主键 id、没有 secretKey</b>：业务码跨环境稳定，
 * 而主键 id 换个部署就变，落到数仓里无法归因。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RequestMsgDto(String schema,
                            String traceId,
                            String spanId,
                            String nodeId,
                            String receivedAt,
                            String protocol,
                            Boolean stream,
                            String stickyKey,
                            RoutingSnapshot routing) implements Msg {

    private static final String SCHEMA = "knot.gateway.request/1.0";

    /**
     * 请求层维度快照：供多维统计直接切片。
     *
     * @param targetType     首个候选的类型（MODEL / MODEL_POOL）
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
     * 由请求上下文与路由结果构建。
     *
     * @param exchange 请求载体，用于读取 {@code stream} 开关（取不到就是 null）
     */
    public static RequestMsgDto of(GatewayRequestContext context,
                                   ResolvedRouting routing,
                                   GatewayExchange exchange) {
        GatewayRoutingInfo info = routing == null ? null : routing.routingInfo();
        return new RequestMsgDto(
            SCHEMA,
            context == null ? null : context.traceId(),
            context == null ? null : context.spanId(),
            Msg.NODE_ID,
            context == null ? null : iso(context.receivedAtMillis()),
            context == null || context.protocol() == null ? null : context.protocol().code(),
            streamOf(exchange),
            context == null ? null : context.stickyKey(),
            routingSnapshot(routing, info));
    }

    private static RoutingSnapshot routingSnapshot(ResolvedRouting routing, GatewayRoutingInfo info) {
        if (routing == null) {
            return null;
        }
        GatewayRoutingInfo.RuleInfo rule = info == null ? null : info.rule();
        GatewayRoutingInfo.ConsumerInfo consumer = info == null ? null : info.consumer();
        GatewayRoutingInfo.AppInfo app = info == null ? null : info.app();
        GatewayRoutingInfo.DepartmentInfo dept = info == null ? null : info.department();
        GatewayRoutingInfo.UserInfo consumerUser = info == null ? null : info.consumerUser();
        GatewayRoutingInfo.UserInfo owner = info == null ? null : info.appOwner();
        List<org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto> candidates = routing.candidateModels();
        String targetType = candidates == null || candidates.isEmpty()
            ? null
            : candidates.get(0).targetType();
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
