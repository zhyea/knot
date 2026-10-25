package org.chobit.knot.plugin.moderation;

import org.chobit.knot.gateway.constants.enums.ModerationActionEnum;
import org.chobit.knot.gateway.constants.enums.ModerationScopeEnum;
import org.chobit.knot.plugin.components.ModerationWordCache;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.gateway.GatewayRoutingSnapshot;
import org.chobit.knot.gateway.util.TextNormalizer;
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
 * <p>是否拦截走插件实例启停（未启用则不分发到本守卫）。生效动作/方向来自本模块的
 * {@link PluginProperties}（{@code knot.plugin.moderation.*}，经 IOC 注入）；
 * 全量词条来自 {@link GatewayPluginContext#words()}——网关每请求下发最新词库资源，
 * 守卫经 IOC 取 {@link ModerationWordCache} 编译自动机（内容版本去重），<b>不持有缓存 / 配置源，不碰持久层</b>。</p>
 *
 * <p>故障模式 <b>fail-open</b>：词库不可用（空词表）时放行，内容安全自身故障不挂全站。</p>
 */
public class ModerationGuard {

    private static final String DIRECTION_REQUEST = ModerationScopeEnum.REQUEST.code();
    private static final String DIRECTION_RESPONSE = ModerationScopeEnum.RESPONSE.code();

    private final ModerationEventSink eventSink;
    private final ModerationWordCache wordCache;
    private final PluginProperties properties;

    /**
     * Constructs a new instance.
     */
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
