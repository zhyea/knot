package org.chobit.knot.gateway.plugin.moderation;

import org.chobit.knot.gateway.constants.enums.ModerationActionEnum;
import org.chobit.knot.gateway.constants.enums.ModerationScopeEnum;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.gateway.GatewayRoutingSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 敏感词审核守卫：被同步插件（{@link SensitiveWordRequestPlugin} / {@link SensitiveWordResponsePlugin}）调用，
 * <b>不硬编码进网关主链路</b>。
 *
 * <p>入站（GATEWAY_REQUEST 阶段）：该阶段位于 {@code proxy} 之前，也就天然早于频控/额度检查，
 * 命中即抛 {@link ModerationRejectException} → 400，<b>不转发上游、不计费、不占 RPM 事前计数</b>。</p>
 *
 * <p>出站（GATEWAY_RESPONSE 阶段）：位于用量记账之后，命中即不交付原响应体直接 400；
 * 上游成本已产生，<b>费用照记</b>。流式响应在此阶段拿到的是 {@code StreamingResponseBody}，不做检测（M2）。</p>
 *
 * <p>故障模式 <b>fail-open</b>：词库加载失败时放行并告警，内容安全自身故障不挂全站。</p>
 */
public class ModerationGuard {

    private static final Logger log = LoggerFactory.getLogger(ModerationGuard.class);

    private static final String DIRECTION_REQUEST = ModerationScopeEnum.REQUEST.code();
    private static final String DIRECTION_RESPONSE = ModerationScopeEnum.RESPONSE.code();

    private final ModerationProperties properties;
    private final ModerationSnapshotCache snapshotCache;
    private final ModerationEventSink eventSink;

    /**
     * Constructs a new instance.
     */
    public ModerationGuard(ModerationProperties properties,
                           ModerationSnapshotCache snapshotCache,
                           ModerationEventSink eventSink) {
        this.properties = properties;
        this.snapshotCache = snapshotCache;
        this.eventSink = eventSink;
    }

    /**
     * 入站检测：命中按当前动作处置（BLOCK → 400）。
     */
    public void checkRequest(GatewayPluginContext context) {
        if (!properties.isEnabled() || !properties.scopeEnum().coversRequest()) {
            return;
        }
        check(context, DIRECTION_REQUEST, ModerationTextExtractor.fromRequest(context.requestBody()));
    }

    /**
     * 整包出站检测；流式响应体拿不到文本，走空列表直接放行（M2 增量检测）。
     */
    public void checkResponse(GatewayPluginContext context) {
        if (!properties.isEnabled() || !properties.scopeEnum().coversResponse()) {
            return;
        }
        check(context, DIRECTION_RESPONSE, ModerationTextExtractor.fromResponseObject(context.responseBody()));
    }

    private void check(GatewayPluginContext context, String direction, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return;
        }
        ModerationSnapshot snapshot;
        try {
            snapshot = snapshotCache.snapshot();
        } catch (RuntimeException e) {
            // fail-open（常量 FAIL_MODE=OPEN）：审核自身故障不阻断业务
            log.warn("Moderation word list unavailable, fail-open: {}", e.getMessage());
            return;
        }
        if (snapshot == null || snapshot.empty()) {
            return;
        }
        List<AhoCorasickMatcher.Hit> effective = scan(texts, snapshot.matcher());
        if (effective.isEmpty()) {
            return;
        }
        AhoCorasickMatcher.Hit top = effective.stream()
                .max(Comparator.comparingInt(AhoCorasickMatcher.Hit::severity))
                .orElseThrow();
        ModerationActionEnum action = properties.actionEnum();
        eventSink.emit(ModerationHitEvent.of(
                context == null ? null : context.traceId(),
                appCodeOf(context),
                context == null ? null : context.ruleCode(),
                direction,
                top.categoryCode(),
                top.severity(),
                action.code(),
                effective.size(),
                matchedLengthOf(effective),
                snapshot.version()));
        if (action == ModerationActionEnum.BLOCK) {
            throw new ModerationRejectException(
                    "Content moderation blocked the " + direction.toLowerCase() + " by sensitive word",
                    top.categoryCode());
        }
        if (action == ModerationActionEnum.MASK) {
            // MASK 需要归一化副本 → 原文的 offset 映射并在原 body 上改写，留 M2 与出站一起做
            log.warn("MASK action is not implemented yet, falling back to FLAG");
        }
    }

    private List<AhoCorasickMatcher.Hit> scan(List<String> texts, AhoCorasickMatcher matcher) {
        List<AhoCorasickMatcher.Hit> hits = new ArrayList<>();
        for (String text : texts) {
            if (text == null || text.isEmpty() || text.length() > ModerationConstants.MAX_TEXT_LENGTH) {
                continue;
            }
            String normalized = TextNormalizer.normalize(text);
            if (normalized == null || normalized.isEmpty()) {
                continue;
            }
            for (AhoCorasickMatcher.Hit hit : matcher.scan(normalized)) {
                if (hit.severity() >= ModerationConstants.SEVERITY_THRESHOLD) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private static int matchedLengthOf(List<AhoCorasickMatcher.Hit> hits) {
        int total = 0;
        for (AhoCorasickMatcher.Hit hit : hits) {
            total += hit.end() - hit.start();
        }
        return total;
    }

    private static String appCodeOf(GatewayPluginContext context) {
        GatewayRoutingSnapshot snapshot = context == null ? null : context.routingSnapshot();
        return snapshot == null ? null : snapshot.getAppId();
    }
}
