package org.chobit.knot.plugin.moderation;

import org.chobit.knot.common.constants.enums.ModerationActionEnum;
import org.chobit.knot.common.constants.enums.ModerationScopeEnum;
import org.chobit.knot.plugin.components.ModerationWordCache;
import org.chobit.knot.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.plugin.gateway.GatewayRoutingSnapshot;
import org.chobit.knot.common.util.TextNormalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 敏感词审核守卫：被同步插件（{@link SensitiveWordRequestPlugin} / {@link SensitiveWordResponsePlugin}）调用，不硬编码进网关主链路。
 *
 * 入站（GATEWAY_REQUEST，{@code proxy} 之前）：命中即抛 {@link ModerationRejectException} → 400，不转发上游、不计费、不占 RPM 事前计数。
 * 出站（GATEWAY_RESPONSE，用量记账之后）：命中即 400，上游成本已产生、费用照记；流式响应（{@code StreamingResponseBody}）不做检测。
 * 生效动作 / 方向取自 {@link PluginProperties}（{@code knot.plugin.moderation.*}）；故障模式 fail-open，词表为空时放行。
 */
public class ModerationGuard {

    private static final String DIRECTION_REQUEST = ModerationScopeEnum.REQUEST.code();
    private static final String DIRECTION_RESPONSE = ModerationScopeEnum.RESPONSE.code();

    private final ModerationEventSink eventSink;
    private final ModerationWordCache wordCache;
    private final PluginProperties properties;

    public ModerationGuard(ModerationEventSink eventSink,
                           ModerationWordCache wordCache,
                           PluginProperties properties) {
        this.eventSink = eventSink;
        this.wordCache = wordCache;
        this.properties = properties;
    }

    /**
     * 入站检测：命中按当前动作处置（BLOCK → 400）。
     */
    public void checkRequest(GatewayPluginContext context) {
        PluginProperties.Moderation moderation = properties.getModeration();
        if (!moderation.scopeEnum().coversRequest()) {
            return;
        }
        check(context, moderation, wordCache.snapshotFor(context.words()), DIRECTION_REQUEST,
                ModerationTextExtractor.fromRequest(context.requestBody()));
    }

    /**
     * 整包出站检测；流式响应体拿不到文本，走空列表直接放行（M2 增量检测）。
     */
    public void checkResponse(GatewayPluginContext context) {
        PluginProperties.Moderation moderation = properties.getModeration();
        if (!moderation.scopeEnum().coversResponse()) {
            return;
        }
        check(context, moderation, wordCache.snapshotFor(context.words()), DIRECTION_RESPONSE,
                ModerationTextExtractor.fromResponseObject(context.responseBody()));
    }

    private void check(GatewayPluginContext context,
                       PluginProperties.Moderation moderation,
                       ModerationSnapshot snapshot,
                       String direction,
                       List<String> texts) {
        if (snapshot == null || snapshot.empty() || texts == null || texts.isEmpty()) {
            return;
        }
        List<AhoCorasickMatcher.Hit> effective = scan(texts, snapshot.matcher());
        if (effective.isEmpty()) {
            return;
        }
        AhoCorasickMatcher.Hit top = effective.stream()
                .max(Comparator.comparingInt(AhoCorasickMatcher.Hit::severity))
                .orElseThrow();
        ModerationActionEnum action = moderation.actionEnum();
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
