package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.kafka.CallTraceContext;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.model.usage.KnotExtendPayload;
import org.chobit.knot.gateway.model.usage.ModelUsage;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * <b>响应层</b>消息：一次客户端请求的终态——结果、用量、计费、错误与上游调用明细。
 *
 * <p>与请求层靠 {@code traceId} 关联。请求缺失（响应层有、请求层没有）= 入口即拒
 * （鉴权 / Rule 头校验就没通过），这是设计口径而非丢消息：数仓 side left join 时能直接分辨出来。</p>
 *
 * <p><b>可空字段的语义</b>：{@code ttftMs} / {@code hit} / {@code usage} / {@code billing} 在
 * 失败路径恒为 {@code null}，且 {@code null} 与 {@code 0} 必须区分——
 * {@code ttftMs=0} 是「首 token 极快」，{@code null} 是「没有首 token」。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ResponseMsgDto(String schema,
                             String traceId,
                             String spanId,
                             String nodeId,
                             String receivedAt,
                             String finishedAt,
                             long durationMs,
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

    private static final String SCHEMA = "knot.gateway.response/1.0";

    /**
     * 实际承接本次请求的上游侧标识。
     *
     * @param modelCode           KB 侧模型业务码（也是响应头 X-Knot-Model 的值）
     * @param upstreamModelCode   实际写入请求体 model 的上游标识（可能与前者不同）
     * @param providerAccountCode 供应商账户业务码
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record HitSnapshot(String modelCode,
                              String upstreamModelCode,
                              String providerAccountCode) {
    }

    /**
     * 由请求上下文与本次调用产物构建终态消息。
     *
     * @param ttftMs 首 token 耗时（毫秒）；{@code null} 表示没有成功首 token
     * @param usage  本次调用的用量账目；失败路径为 {@code null}
     * @param error  终止本次请求的异常；成功路径为 {@code null}
     */
    public static ResponseMsgDto of(GatewayRequestContext context,
                                    CallTraceContext trace,
                                    Long ttftMs,
                                    UsageAccounting usage,
                                    Throwable error) {
        long receivedAtMillis = context == null ? 0L : context.receivedAtMillis();
        long finishedAtMillis = System.currentTimeMillis();
        ErrorMsgDto errorRecord = ErrorMsgDto.of(error);
        KnotExtendPayload payload = usage == null ? null : KnotExtendPayload.of(usage);
        return new ResponseMsgDto(
            SCHEMA,
            context == null ? null : context.traceId(),
            context == null ? null : context.spanId(),
            Msg.NODE_ID,
            iso(receivedAtMillis),
            iso(finishedAtMillis),
            Math.max(0L, finishedAtMillis - receivedAtMillis),
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

    private static HitSnapshot hitSnapshot(GatewayRequestContext context) {
        if (context == null) {
            return null;
        }
        RoutingRuleTargetDto hit = context.attribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE);
        if (hit == null) {
            return null;
        }
        return new HitSnapshot(hit.targetCode(), hit.upstreamModelCode(), hit.providerAccountCode());
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

    private static String iso(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .toOffsetDateTime()
            .toString();
    }
}
