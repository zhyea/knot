package org.chobit.knot.gateway.routing;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
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
import org.chobit.knot.gateway.model.RetryPolicy;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.util.tools.RoutingSecretKeyGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 根据消费者 API Key 和路由规则编码解析本次网关请求的路由信息。
 */
@Component
@RequiredArgsConstructor
public class RoutingResolver {

    private final GatewayDataService dataService;
    private final ModelPoolSelection modelPoolSelection;

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
        if (!EnabledStatusEnum.isEnabled(consumer.getStatus())) {
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
        if (!EnabledStatusEnum.isEnabled(app.getStatus())) {
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
                routingInfo,
                RetryPolicy.parse(rule.getRetryPolicy()));
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
                        rule.getUsername()
                ),
                new GatewayRoutingInfo.ConsumerInfo(
                        consumer.getId(),
                        consumer.getConsumerCode(),
                        consumer.getName(),
                        consumer.getSecretKey(),
                        Boolean.TRUE.equals(consumer.getReturnUsageDetail())
                ),
                new GatewayRoutingInfo.AppInfo(app.getId(), app.getAppCode(), app.getName(), app.getDeptCode()),
                new GatewayRoutingInfo.UserInfo(consumer.getUserUsername(), consumer.getUserRealName()),
                new GatewayRoutingInfo.UserInfo(app.getOwnerUsername(), app.getOwnerRealName()),
                new GatewayRoutingInfo.DepartmentInfo(app.getDeptCode(), null)
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
                // 排序键用存储主键 targetCode（NOT NULL）；targetId 是 left join 派生的可空值，
                // 用它做 natural ordering 会在目标被删/JOIN 未命中时抛 NPE
                .sorted(Comparator.comparingInt(RoutingRuleTargetDto::priority).reversed()
                        .thenComparing(RoutingRuleTargetDto::targetCode)
                        .thenComparing(RoutingRuleTargetDto::targetType))
                .forEach(ordered::add);
        return ordered;
    }

    /**
     * 身份去重：同一路由目标（存储主键 targetCode + 目标类型）只保留一条。
     * 用存储主键而非派生的 targetId，对齐「跨模块绑定存 code 不存 id」口径。
     * 不手写整对象字段比对——那是字段清单的第二份真相，加字段必漏且编译器不报。
     */
    private boolean sameTarget(RoutingRuleTargetDto left, RoutingRuleTargetDto right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.targetCode(), right.targetCode())
                && Objects.equals(left.targetType(), right.targetType());
    }

    private List<RoutingRuleTargetDto> listTargets(Long ruleId) {
        return dataService.listTargetsByRuleId(ruleId).stream()
                .map(entity -> new RoutingRuleTargetDto(
                        entity.getTargetType(),
                        entity.getTargetId(),
                        entity.getTargetCode(),
                        null,
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
            // 按存储主键 targetCode 取模型：targetId 是 left join 派生的可空值（目标被删即失效）
            ModelEntity model = dataService.getModelByCode(target.targetCode());
            if (model == null || !EnabledStatusEnum.isEnabled(model.getStatus())) {
                return List.of();
            }
            return List.of(new RoutingRuleTargetDto(
                    RouteTargetTypeEnum.MODEL.code(),
                    model.getId(),
                    model.getModelCode(),
                    model.getUpstreamModel(),
                    model.getModelCode(),
                    model.getModelType(),
                    model.getProviderAccountCode(),
                    target.priority(),
                    target.primary()
            ));
        }
        if (!RouteTargetTypeEnum.MODEL_POOL.code().equals(target.targetType())) {
            return List.of();
        }
        ModelPoolEntity pool = dataService.getModelPoolByCode(target.targetCode());
        if (pool == null || !EnabledStatusEnum.isEnabled(pool.getStatus())) {
            return List.of();
        }
        return resolvePoolCandidates(pool, target);
    }

    private List<RoutingRuleTargetDto> resolvePoolCandidates(ModelPoolEntity pool, RoutingRuleTargetDto target) {
        // 池条目只存 model_code，模型实体（含主键 id，下游取凭据/协议绑定用）按 code 从缓存解析
        List<PoolItemCandidate> available = dataService.listModelPoolItemsByPoolCode(pool.getPoolCode()).stream()
                .filter(item -> EnabledStatusEnum.isEnabled(item.getStatus()))
                .map(item -> new PoolItemCandidate(item, dataService.getModelByCode(item.getModelCode())))
                .filter(candidate -> candidate.model() != null
                        && EnabledStatusEnum.isEnabled(candidate.model().getStatus()))
                .toList();
        // 池内选中顺序由模型池的 selection_strategy 决定：首个即本次选中，其余为故障转移候选
        List<PoolItemCandidate> ordered = modelPoolSelection.order(
                available,
                pool.getSelectionStrategy(),
                candidate -> poolItemPriority(candidate.item()),
                candidate -> poolItemWeight(candidate.item()),
                candidate -> candidate.item().getId() != null ? candidate.item().getId() : 0L
        );
        return ordered.stream()
                .map(candidate -> new RoutingRuleTargetDto(
                        RouteTargetTypeEnum.MODEL.code(),
                        candidate.model().getId(),
                        candidate.item().getModelCode(),
                        candidate.model().getUpstreamModel(),
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
