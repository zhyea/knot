package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.TrafficResourceType;
import org.chobit.knot.gateway.dto.routing.RoutingRuleModelDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.entity.RoutingRuleModelEntity;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.mapper.RoutingConsumerMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleModelMapper;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.util.tools.RoutingSecretKeyGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消费者 API Key 鉴权：sk- 前缀密钥绑定消费者，消费者再关联多个路由规则。
 */
@Service
public class RoutingAuthService {

    private final RoutingRuleMapper routingRuleMapper;
    private final RoutingRuleModelMapper routingRuleModelMapper;
    private final RoutingConsumerMapper routingConsumerMapper;
    private final AppMapper appMapper;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    public RoutingAuthService(RoutingRuleMapper routingRuleMapper,
                              RoutingRuleModelMapper routingRuleModelMapper,
                              RoutingConsumerMapper routingConsumerMapper,
                              AppMapper appMapper,
                              ResourceTrafficPolicySupport trafficPolicySupport) {
        this.routingRuleMapper = routingRuleMapper;
        this.routingRuleModelMapper = routingRuleModelMapper;
        this.routingConsumerMapper = routingConsumerMapper;
        this.appMapper = appMapper;
        this.trafficPolicySupport = trafficPolicySupport;
    }

    public record ResolvedRouting(
            Long ruleId,
            String ruleCode,
            String secretKey,
            RateLimitService.AppContext appContext,
            RoutingRuleModelDto primaryModel
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
        if (consumer == null || !"ENABLED".equals(consumer.getStatus())) {
            return null;
        }

        RuleSelection selection = findRule(consumer.getId(), requestedModel);
        RoutingRuleEntity rule = selection != null ? selection.rule() : null;
        if (rule == null) {
            return null;
        }
        AppEntity app = appMapper.getById(rule.getAppId());
        if (app == null || !"ENABLED".equals(app.getStatus())) {
            return null;
        }
        ResourceTrafficPolicySupport.TrafficPolicies appTraffic =
                trafficPolicySupport.load(TrafficResourceType.APP, app.getId());
        RateLimitPolicy rate = appTraffic != null ? appTraffic.rateLimitPolicy() : null;
        QuotaPolicy quota = appTraffic != null ? appTraffic.quotaPolicy() : null;
        RateLimitService.AppContext appContext = new RateLimitService.AppContext(
                app.getId(), app.getAppId(), app.getName(), rate, quota
        );
        RoutingRuleModelDto primary = selection.model() != null ? selection.model() : findPrimaryModel(rule.getId());
        if (primary == null) {
            return null;
        }
        return new ResolvedRouting(rule.getId(), rule.getRuleCode(), consumer.getSecretKey(), appContext, primary);
    }

    private record RuleSelection(RoutingRuleEntity rule, RoutingRuleModelDto model) {
    }

    private RuleSelection findRule(Long consumerId, String requestedModel) {
        List<RoutingRuleEntity> rules = routingRuleMapper.listEnabledByConsumerId(consumerId);
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        String modelCode = requestedModel != null ? requestedModel.trim() : "";
        if (!modelCode.isEmpty()) {
            for (RoutingRuleEntity rule : rules) {
                RoutingRuleModelDto model = findModelByCode(rule.getId(), modelCode);
                if (model != null) {
                    return new RuleSelection(rule, model);
                }
            }
            return null;
        }
        return new RuleSelection(rules.get(0), null);
    }

    private RoutingRuleModelDto findModelByCode(Long ruleId, String modelCode) {
        List<RoutingRuleModelEntity> bindings = routingRuleModelMapper.listByRuleId(ruleId);
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        return bindings.stream()
                .filter(e -> modelCode.equals(e.getModelCode()))
                .findFirst()
                .map(this::toModelDto)
                .orElse(null);
    }

    private RoutingRuleModelDto findPrimaryModel(Long ruleId) {
        List<RoutingRuleModelEntity> bindings = routingRuleModelMapper.listByRuleId(ruleId);
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        RoutingRuleModelEntity primary = bindings.stream()
                .filter(e -> Boolean.TRUE.equals(e.getPrimary()))
                .findFirst()
                .orElse(bindings.get(0));
        return toModelDto(primary);
    }

    private RoutingRuleModelDto toModelDto(RoutingRuleModelEntity entity) {
        return new RoutingRuleModelDto(
                entity.getModelId(),
                entity.getModelCode(),
                entity.getModelName(),
                entity.getProviderId(),
                entity.getPriority() != null ? entity.getPriority() : 100,
                Boolean.TRUE.equals(entity.getPrimary())
        );
    }
}
