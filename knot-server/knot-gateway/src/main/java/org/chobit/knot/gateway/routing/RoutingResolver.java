package org.chobit.knot.gateway.routing;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.ModelPoolItemEntity;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.GatewayRoutingInfo;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.util.tools.RoutingSecretKeyGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 根据消费者 API Key 和路由规则编码解析本次网关请求的路由信息。
 */
@Component
@RequiredArgsConstructor
public class RoutingResolver {

    private final GatewayDataService dataService;

    /**
     * Resolves the requested value from current context and configuration. Executes the public operation.
     */
    public ResolvedRouting resolveByRule(String secretKey, String ruleCode) {
        if (!RoutingSecretKeyGenerator.isRoutingSecretKey(secretKey)) {
            throw new GatewayAuthException("Invalid consumer API key");
        }
        if (StringUtils.isBlank(ruleCode)) {
            throw new GatewayInvalidRequestException("Rule header must not be blank");
        }
        RoutingConsumerEntity consumer = dataService.getConsumerBySecretKey(StringUtils.trim(secretKey));
        if (consumer == null) {
            throw new GatewayAuthException("Consumer API key not found");
        }
        if (!EntityStatusEnum.ENABLED.code().equals(consumer.getStatus())) {
            throw new GatewayAuthException("Consumer is disabled");
        }
        RoutingRuleEntity rule = dataService.getEnabledRuleByConsumerAndCode(consumer.getId(), StringUtils.trim(ruleCode));
        if (rule == null) {
            throw new GatewayAuthException("Routing rule is not available for this consumer");
        }
        AppEntity app = dataService.getAppById(rule.getAppId());
        if (app == null) {
            throw new GatewayAuthException("Bound app not found");
        }
        if (!EntityStatusEnum.ENABLED.code().equals(app.getStatus())) {
            throw new GatewayAuthException("Bound app is disabled");
        }
        List<RoutingRuleTargetDto> candidates = resolveCandidateModels(rule.getId());
        if (candidates.isEmpty()) {
            throw new GatewayUpstreamException(
                    "No enabled routing target is available",
                    ProxyErrorCodeEnum.NO_ROUTING_TARGET.code()
            );
        }
        GatewayRoutingInfo routingInfo = buildRoutingInfo(rule, consumer, app);
        return new ResolvedRouting(rule.getId(),
                rule.getRuleCode(),
                consumer.getId(),
                consumer.getSecretKey(),
                Boolean.TRUE.equals(consumer.getReturnUsageDetail()),
                candidates,
                routingInfo);
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
                new GatewayRoutingInfo.AppInfo(app.getId(), app.getAppId(), app.getName(), app.getDeptId()),
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
            candidates.addAll(resolveTargetCandidates(target));
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
                        entity.getProviderAccountCode(),
                        entity.getPriority() != null ? entity.getPriority() : 100,
                        Boolean.TRUE.equals(entity.getPrimary())
                ))
                .toList();
    }

    private List<RoutingRuleTargetDto> resolveTargetCandidates(RoutingRuleTargetDto target) {
        if (RouteTargetTypeEnum.MODEL.code().equals(target.targetType())) {
            ModelEntity model = dataService.getModelById(target.targetId());
            if (model == null || !EntityStatusEnum.ENABLED.code().equals(model.getStatus())) {
                return List.of();
            }
            return List.of(new RoutingRuleTargetDto(
                    RouteTargetTypeEnum.MODEL.code(),
                    model.getId(),
                    model.getModelCode(),
                    model.getName(),
                    model.getModelType(),
                    model.getProviderAccountCode(),
                    target.priority(),
                    target.primary()
            ));
        }
        if (!RouteTargetTypeEnum.MODEL_POOL.code().equals(target.targetType())) {
            return List.of();
        }
        ModelPoolEntity pool = dataService.getModelPoolById(target.targetId());
        if (pool == null || !EntityStatusEnum.ENABLED.code().equals(pool.getStatus())) {
            return List.of();
        }
        return resolvePoolCandidates(pool, target);
    }

    private List<RoutingRuleTargetDto> resolvePoolCandidates(ModelPoolEntity pool, RoutingRuleTargetDto target) {
        // 池条目只存 model_code，模型实体（含主键 id，下游取凭据/协议绑定用）按 code 从缓存解析
        Comparator<PoolItemCandidate> byPriority =
                Comparator.comparingInt(candidate -> poolItemPriority(candidate.item()));
        Comparator<PoolItemCandidate> byWeight =
                Comparator.comparingInt(candidate -> poolItemWeight(candidate.item()));
        return dataService.listModelPoolItemsByPoolCode(pool.getPoolCode()).stream()
                .filter(item -> EntityStatusEnum.ENABLED.code().equals(item.getStatus()))
                .map(item -> new PoolItemCandidate(item, dataService.getModelByCode(item.getModelCode())))
                .filter(candidate -> candidate.model() != null
                        && EntityStatusEnum.ENABLED.code().equals(candidate.model().getStatus()))
                .sorted(byPriority.reversed()
                        .thenComparing(byWeight.reversed())
                        .thenComparing(candidate -> candidate.item().getId()))
                .map(candidate -> new RoutingRuleTargetDto(
                        RouteTargetTypeEnum.MODEL.code(),
                        candidate.model().getId(),
                        candidate.item().getModelCode(),
                        candidate.item().getModelName(),
                        candidate.item().getModelType(),
                        candidate.item().getProviderAccountCode(),
                        target.priority(),
                        target.primary()
                ))
                .toList();
    }

    private record PoolItemCandidate(ModelPoolItemEntity item, ModelEntity model) {
    }

    private int poolItemPriority(ModelPoolItemEntity item) {
        return item.getPriority() != null ? item.getPriority() : 100;
    }

    private int poolItemWeight(ModelPoolItemEntity item) {
        return item.getWeight() != null ? item.getWeight() : 100;
    }
}
