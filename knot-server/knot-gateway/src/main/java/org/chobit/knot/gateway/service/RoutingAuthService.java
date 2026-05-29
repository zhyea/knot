package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.EntityStatus;
import org.chobit.knot.gateway.constants.RouteTargetTypes;
import org.chobit.knot.gateway.constants.TrafficResourceType;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.ModelPoolItemEntity;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ModelPoolMapper;
import org.chobit.knot.gateway.mapper.RoutingConsumerMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleTargetMapper;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.util.tools.RoutingSecretKeyGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 消费者 API Key 鉴权：sk- 前缀密钥绑定消费者，消费者再关联多个路由规则。
 */
@Service
public class RoutingAuthService {

    private final RoutingRuleMapper routingRuleMapper;
    private final RoutingRuleTargetMapper routingRuleTargetMapper;
    private final RoutingConsumerMapper routingConsumerMapper;
    private final ModelMapper modelMapper;
    private final ModelPoolMapper modelPoolMapper;
    private final AppMapper appMapper;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    public RoutingAuthService(RoutingRuleMapper routingRuleMapper,
                              RoutingRuleTargetMapper routingRuleTargetMapper,
                              RoutingConsumerMapper routingConsumerMapper,
                              ModelMapper modelMapper,
                              ModelPoolMapper modelPoolMapper,
                              AppMapper appMapper,
                              ResourceTrafficPolicySupport trafficPolicySupport) {
        this.routingRuleMapper = routingRuleMapper;
        this.routingRuleTargetMapper = routingRuleTargetMapper;
        this.routingConsumerMapper = routingConsumerMapper;
        this.modelMapper = modelMapper;
        this.modelPoolMapper = modelPoolMapper;
        this.appMapper = appMapper;
        this.trafficPolicySupport = trafficPolicySupport;
    }

    public record ResolvedRouting(
            Long ruleId,
            String ruleCode,
            Long consumerId,
            String secretKey,
            boolean returnUsageDetail,
            RateLimitService.AppContext appContext,
            RoutingRuleTargetDto resolvedModel,
            List<RoutingRuleTargetDto> candidateModels
    ) {
    }

    public ResolvedRouting resolve(String secretKey) {
        return resolve(secretKey, null);
    }

    public ResolvedRouting resolve(String secretKey, String requestedModel) {
        if (!RoutingSecretKeyGenerator.isRoutingSecretKey(secretKey)) {
            return null;
        }
        RoutingConsumerEntity consumer = routingConsumerMapper.getBySecretKey(secretKey.trim());
        if (consumer == null || !EntityStatus.ENABLED.equals(consumer.getStatus())) {
            return null;
        }

        RuleSelection selection = findRule(consumer.getId(), requestedModel);
        RoutingRuleEntity rule = selection != null ? selection.rule() : null;
        if (rule == null) {
            return null;
        }
        AppEntity app = appMapper.getById(rule.getAppId());
        if (app == null || !EntityStatus.ENABLED.equals(app.getStatus())) {
            return null;
        }
        ResourceTrafficPolicySupport.TrafficPolicies appTraffic =
                trafficPolicySupport.load(TrafficResourceType.APP, app.getId());
        ResourceTrafficPolicySupport.TrafficPolicies consumerTraffic =
                trafficPolicySupport.load(TrafficResourceType.ROUTING_CONSUMER, consumer.getId());
        RateLimitPolicy rate = mergeRateLimit(
                appTraffic != null ? appTraffic.rateLimitPolicy() : null,
                consumerTraffic != null ? consumerTraffic.rateLimitPolicy() : null
        );
        QuotaPolicy quota = mergeQuota(
                appTraffic != null ? appTraffic.quotaPolicy() : null,
                consumerTraffic != null ? consumerTraffic.quotaPolicy() : null
        );
        RateLimitService.AppContext appContext = new RateLimitService.AppContext(
                app.getId(), app.getAppId(), app.getName(), rate, quota
        );
        List<RoutingRuleTargetDto> candidates = resolveCandidateModels(rule.getId(), selection.target());
        if (candidates.isEmpty()) {
            return null;
        }
        return new ResolvedRouting(rule.getId(), rule.getRuleCode(), consumer.getId(), consumer.getSecretKey(),
                Boolean.TRUE.equals(consumer.getReturnUsageDetail()), appContext, candidates.get(0), candidates);
    }

    public ResolvedRouting resolveByRule(String secretKey, String ruleCode) {
        if (!RoutingSecretKeyGenerator.isRoutingSecretKey(secretKey) || ruleCode == null || ruleCode.isBlank()) {
            return null;
        }
        RoutingConsumerEntity consumer = routingConsumerMapper.getBySecretKey(secretKey.trim());
        if (consumer == null || !EntityStatus.ENABLED.equals(consumer.getStatus())) {
            return null;
        }
        RoutingRuleEntity rule = routingRuleMapper.getEnabledByConsumerIdAndRuleCode(consumer.getId(), ruleCode.trim());
        if (rule == null) {
            return null;
        }
        AppEntity app = appMapper.getById(rule.getAppId());
        if (app == null || !EntityStatus.ENABLED.equals(app.getStatus())) {
            return null;
        }
        ResourceTrafficPolicySupport.TrafficPolicies appTraffic =
                trafficPolicySupport.load(TrafficResourceType.APP, app.getId());
        ResourceTrafficPolicySupport.TrafficPolicies consumerTraffic =
                trafficPolicySupport.load(TrafficResourceType.ROUTING_CONSUMER, consumer.getId());
        RateLimitPolicy rate = mergeRateLimit(
                appTraffic != null ? appTraffic.rateLimitPolicy() : null,
                consumerTraffic != null ? consumerTraffic.rateLimitPolicy() : null
        );
        QuotaPolicy quota = mergeQuota(
                appTraffic != null ? appTraffic.quotaPolicy() : null,
                consumerTraffic != null ? consumerTraffic.quotaPolicy() : null
        );
        RateLimitService.AppContext appContext = new RateLimitService.AppContext(
                app.getId(), app.getAppId(), app.getName(), rate, quota
        );
        List<RoutingRuleTargetDto> candidates = resolveCandidateModels(rule.getId(), null);
        if (candidates.isEmpty()) {
            return null;
        }
        return new ResolvedRouting(rule.getId(), rule.getRuleCode(), consumer.getId(), consumer.getSecretKey(),
                Boolean.TRUE.equals(consumer.getReturnUsageDetail()), appContext, candidates.get(0), candidates);
    }

    private record RuleSelection(RoutingRuleEntity rule, RoutingRuleTargetDto target) {
    }

    private RuleSelection findRule(Long consumerId, String requestedModel) {
        List<RoutingRuleEntity> rules = routingRuleMapper.listEnabledByConsumerId(consumerId);
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        String modelCode = requestedModel != null ? requestedModel.trim() : "";
        if (!modelCode.isEmpty()) {
            for (RoutingRuleEntity rule : rules) {
                RoutingRuleTargetDto target = findModelByCode(rule.getId(), modelCode);
                if (target != null) {
                    return new RuleSelection(rule, target);
                }
            }
            return null;
        }
        return new RuleSelection(rules.get(0), null);
    }

    private RoutingRuleTargetDto findModelByCode(Long ruleId, String modelCode) {
        List<RoutingRuleTargetDto> targets = listTargets(ruleId);
        if (targets.isEmpty()) {
            return null;
        }
        for (RoutingRuleTargetDto target : targets) {
            if (!modelCode.equals(target.targetCode())) {
                continue;
            }
            return target;
        }
        return null;
    }

    private RoutingRuleTargetDto findPrimaryModel(Long ruleId) {
        List<RoutingRuleTargetDto> candidates = resolveCandidateModels(ruleId, null);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<RoutingRuleTargetDto> resolveCandidateModels(Long ruleId, RoutingRuleTargetDto preferredTarget) {
        List<RoutingRuleTargetDto> orderedTargets = orderTargets(ruleId, preferredTarget);
        if (orderedTargets.isEmpty()) {
            return List.of();
        }
        List<RoutingRuleTargetDto> candidates = new ArrayList<>();
        for (RoutingRuleTargetDto target : orderedTargets) {
            RoutingRuleTargetDto resolved = resolveTarget(target);
            if (resolved != null) {
                candidates.add(resolved);
            }
        }
        return candidates;
    }

    private List<RoutingRuleTargetDto> orderTargets(Long ruleId, RoutingRuleTargetDto preferredTarget) {
        List<RoutingRuleTargetDto> targets = listTargets(ruleId);
        if (targets.isEmpty()) {
            return List.of();
        }
        RoutingRuleTargetDto first = null;
        if (preferredTarget != null) {
            first = targets.stream()
                    .filter(target -> sameTarget(target, preferredTarget))
                    .findFirst()
                    .orElse(preferredTarget);
        }
        if (first == null) {
            first = targets.stream()
                    .filter(RoutingRuleTargetDto::primary)
                    .findFirst()
                    .orElse(targets.get(0));
        }
        List<RoutingRuleTargetDto> ordered = new ArrayList<>();
        RoutingRuleTargetDto selectedFirst = first;
        ordered.add(selectedFirst);
        targets.stream()
                .filter(target -> !sameTarget(target, selectedFirst))
                .sorted(Comparator.comparingInt(RoutingRuleTargetDto::priority).reversed()
                        .thenComparing(RoutingRuleTargetDto::targetId)
                        .thenComparing(RoutingRuleTargetDto::targetType))
                .forEach(ordered::add);
        return ordered;
    }

    private boolean sameTarget(RoutingRuleTargetDto left, RoutingRuleTargetDto right) {
        if (left == null || right == null) {
            return false;
        }
        return left.targetId() != null
                && left.targetId().equals(right.targetId())
                && left.targetType() != null
                && left.targetType().equals(right.targetType());
    }

    private List<RoutingRuleTargetDto> listTargets(Long ruleId) {
        return routingRuleTargetMapper.listByRuleId(ruleId).stream()
                .map(entity -> new RoutingRuleTargetDto(
                        entity.getTargetType(),
                        entity.getTargetId(),
                        entity.getTargetCode(),
                        entity.getTargetName(),
                        entity.getModelType(),
                        entity.getProviderId(),
                        entity.getPriority() != null ? entity.getPriority() : 100,
                        Boolean.TRUE.equals(entity.getPrimary())
                ))
                .toList();
    }

    private RoutingRuleTargetDto resolveTarget(RoutingRuleTargetDto target) {
        if (RouteTargetTypes.MODEL.equals(target.targetType())) {
            ModelEntity model = modelMapper.getById(target.targetId());
            if (model == null || !EntityStatus.ENABLED.equals(model.getStatus())) {
                return null;
            }
            return new RoutingRuleTargetDto(RouteTargetTypes.MODEL, model.getId(), model.getModelCode(), model.getName(),
                    model.getModelType(), model.getProviderId(), target.priority(), target.primary());
        }
        if (!RouteTargetTypes.MODEL_POOL.equals(target.targetType())) {
            return null;
        }
        ModelPoolEntity pool = modelPoolMapper.getById(target.targetId());
        if (pool == null || !EntityStatus.ENABLED.equals(pool.getStatus())) {
            return null;
        }
        ModelPoolItemEntity item = selectPoolItem(pool);
        if (item == null) {
            return null;
        }
        return new RoutingRuleTargetDto(RouteTargetTypes.MODEL, item.getModelId(), item.getModelCode(), item.getModelName(),
                item.getModelType(), item.getProviderId(), target.priority(), target.primary());
    }

    private ModelPoolItemEntity selectPoolItem(ModelPoolEntity pool) {
        List<ModelPoolItemEntity> candidates = modelPoolMapper.listItemsByPoolId(pool.getId()).stream()
                .filter(item -> EntityStatus.ENABLED.equals(item.getStatus()))
                .filter(item -> {
                    ModelEntity model = modelMapper.getById(item.getModelId());
                    return model != null && EntityStatus.ENABLED.equals(model.getStatus());
                })
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        String strategy = pool.getSelectionStrategy() == null ? "WEIGHTED" : pool.getSelectionStrategy();
        if ("PRIORITY".equals(strategy)) {
            return candidates.get(0);
        }
        if ("RANDOM".equals(strategy)) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        int total = candidates.stream().mapToInt(item -> Math.max(1, item.getWeight() == null ? 100 : item.getWeight())).sum();
        int hit = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (ModelPoolItemEntity item : candidates) {
            cursor += Math.max(1, item.getWeight() == null ? 100 : item.getWeight());
            if (hit < cursor) {
                return item;
            }
        }
        return candidates.get(0);
    }

    private static RateLimitPolicy mergeRateLimit(RateLimitPolicy app, RateLimitPolicy consumer) {
        if (app == null) {
            return consumer;
        }
        if (consumer == null) {
            return app;
        }
        return new RateLimitPolicy(
                stricterPositive(app.perSecond(), consumer.perSecond()),
                stricterPositive(app.perMinute(), consumer.perMinute()),
                consumer.timeWindow() != null ? consumer.timeWindow() : app.timeWindow()
        );
    }

    private static QuotaPolicy mergeQuota(QuotaPolicy app, QuotaPolicy consumer) {
        if (app == null) {
            return consumer;
        }
        if (consumer == null) {
            return app;
        }
        return new QuotaPolicy(
                stricterPositive(app.dailyLimit(), consumer.dailyLimit()),
                stricterPositive(app.monthlyLimit(), consumer.monthlyLimit()),
                stricterPositive(app.tokenLimit(), consumer.tokenLimit()),
                app.alertEnabled() || consumer.alertEnabled()
        );
    }

    private static int stricterPositive(int left, int right) {
        if (left <= 0) {
            return Math.max(0, right);
        }
        if (right <= 0) {
            return left;
        }
        return Math.min(left, right);
    }

    private static long stricterPositive(long left, long right) {
        if (left <= 0) {
            return Math.max(0, right);
        }
        if (right <= 0) {
            return left;
        }
        return Math.min(left, right);
    }
}
