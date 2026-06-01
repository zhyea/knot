package org.chobit.knot.gateway.routing;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelPoolSelectionStrategyEnum;
import org.chobit.knot.gateway.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.ModelPoolItemEntity;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.model.AppContext;
import org.chobit.knot.gateway.model.GatewayRoutingInfo;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.util.tools.RoutingSecretKeyGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 娑堣垂鑰?API Key 閴存潈锛歴k- 鍓嶇紑瀵嗛挜缁戝畾娑堣垂鑰咃紝娑堣垂鑰呭啀鍏宠仈澶氫釜璺敱瑙勫垯銆?
 */
@Component
@RequiredArgsConstructor
public class RoutingResolver {

    private final GatewayDataService dataService;


    /**
     * Resolves the requested value from current context and configuration. Executes the public operation.
     */
    public ResolvedRouting resolveByRule(String secretKey, String ruleCode) {
        if (!RoutingSecretKeyGenerator.isRoutingSecretKey(secretKey) || ruleCode == null || ruleCode.isBlank()) {
            return null;
        }
        RoutingConsumerEntity consumer = dataService.getConsumerBySecretKey(secretKey.trim());
        if (consumer == null || !EntityStatusEnum.ENABLED.code().equals(consumer.getStatus())) {
            return null;
        }
        RoutingRuleEntity rule = dataService.getEnabledRuleByConsumerAndCode(consumer.getId(), ruleCode.trim());
        if (rule == null) {
            return null;
        }
        AppEntity app = dataService.getAppById(rule.getAppId());
        if (app == null || !EntityStatusEnum.ENABLED.code().equals(app.getStatus())) {
            return null;
        }
        AppContext appContext = new AppContext(
                app.getId(), app.getAppId(), app.getName()
        );
        List<RoutingRuleTargetDto> candidates = resolveCandidateModels(rule.getId());
        if (candidates.isEmpty()) {
            return null;
        }
        return new ResolvedRouting(rule.getId(),
                rule.getRuleCode(),
                consumer.getId(),
                consumer.getSecretKey(),
                Boolean.TRUE.equals(consumer.getReturnUsageDetail()),
                appContext,
                candidates.get(0),
                candidates,
                buildRoutingInfo(rule, consumer, app));
    }

    private static GatewayRoutingInfo buildRoutingInfo(RoutingRuleEntity rule,
                                                       RoutingConsumerEntity consumer,
                                                       AppEntity app) {
        return new GatewayRoutingInfo(
                new GatewayRoutingInfo.RuleInfo(
                        rule.getId(),
                        rule.getRuleCode(),
                        rule.getName(),
                        rule.getAppScenario(),
                        rule.getModelTypes()
                ),
                new GatewayRoutingInfo.ConsumerInfo(
                        consumer.getId(),
                        consumer.getConsumerCode(),
                        consumer.getName(),
                        consumer.getSecretKey(),
                        Boolean.TRUE.equals(consumer.getReturnUsageDetail())
                ),
                new GatewayRoutingInfo.AppInfo(app.getId(), app.getAppId(), app.getName()),
                new GatewayRoutingInfo.UserInfo(rule.getUserId(), rule.getUserUsername(), rule.getUserRealName()),
                new GatewayRoutingInfo.UserInfo(consumer.getUserId(), consumer.getUserUsername(), consumer.getUserRealName()),
                new GatewayRoutingInfo.UserInfo(app.getOwnerUserId(), null, app.getOwnerRealName()),
                new GatewayRoutingInfo.DepartmentInfo(app.getDeptId(), null)
        );
    }


    private List<RoutingRuleTargetDto> resolveCandidateModels(Long ruleId) {
        List<RoutingRuleTargetDto> orderedTargets = orderTargets(ruleId);
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

    private List<RoutingRuleTargetDto> orderTargets(Long ruleId) {
        List<RoutingRuleTargetDto> targets = listTargets(ruleId);
        if (targets.isEmpty()) {
            return List.of();
        }
        final RoutingRuleTargetDto first = targets.stream()
                .filter(RoutingRuleTargetDto::primary)
                .findFirst()
                .orElse(targets.get(0));

        List<RoutingRuleTargetDto> ordered = new ArrayList<>();
        ordered.add(first);
        targets.stream()
                .filter(target -> !sameTarget(target, first))
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
        return dataService.listTargetsByRuleId(ruleId).stream()
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
        if (RouteTargetTypeEnum.MODEL.code().equals(target.targetType())) {
            ModelEntity model = dataService.getModelById(target.targetId());
            if (model == null || !EntityStatusEnum.ENABLED.code().equals(model.getStatus())) {
                return null;
            }
            return new RoutingRuleTargetDto(RouteTargetTypeEnum.MODEL.code(), model.getId(), model.getModelCode(), model.getName(),
                    model.getModelType(), model.getProviderId(), target.priority(), target.primary());
        }
        if (!RouteTargetTypeEnum.MODEL_POOL.code().equals(target.targetType())) {
            return null;
        }
        ModelPoolEntity pool = dataService.getModelPoolById(target.targetId());
        if (pool == null || !EntityStatusEnum.ENABLED.code().equals(pool.getStatus())) {
            return null;
        }
        ModelPoolItemEntity item = selectPoolItem(pool);
        if (item == null) {
            return null;
        }
        return new RoutingRuleTargetDto(RouteTargetTypeEnum.MODEL.code(), item.getModelId(), item.getModelCode(), item.getModelName(),
                item.getModelType(), item.getProviderId(), target.priority(), target.primary());
    }

    private ModelPoolItemEntity selectPoolItem(ModelPoolEntity pool) {
        List<ModelPoolItemEntity> candidates = dataService.listModelPoolItemsByPoolId(pool.getId()).stream()
                .filter(item -> EntityStatusEnum.ENABLED.code().equals(item.getStatus()))
                .filter(item -> {
                    ModelEntity model = dataService.getModelById(item.getModelId());
                    return model != null && EntityStatusEnum.ENABLED.code().equals(model.getStatus());
                })
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        ModelPoolSelectionStrategyEnum strategy =
                ModelPoolSelectionStrategyEnum.fromCodeOrDefault(pool.getSelectionStrategy());
        if (ModelPoolSelectionStrategyEnum.PRIORITY == strategy) {
            return candidates.get(0);
        }
        if (ModelPoolSelectionStrategyEnum.RANDOM == strategy) {
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

}
