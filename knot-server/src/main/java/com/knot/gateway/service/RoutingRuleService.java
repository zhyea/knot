package com.knot.gateway.service;

import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.entity.RoutingRuleEntity;
import com.knot.gateway.mapper.RoutingRuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoutingRuleService {
    private final RoutingRuleMapper routingRuleMapper;

    public RoutingRuleService(RoutingRuleMapper routingRuleMapper) {
        this.routingRuleMapper = routingRuleMapper;
    }

    public List<RoutingRuleDto> list() {
        return routingRuleMapper.list().stream().map(this::toDto).toList();
    }

    public RoutingRuleDto create(RoutingRuleDto request) {
        RoutingRuleEntity entity = toEntity(request);
        entity.setStatus(request.enabled() ? "ENABLED" : "DISABLED");
        routingRuleMapper.insert(entity);
        return toDto(entity);
    }

    public RoutingRuleDto update(Long id, RoutingRuleDto request) {
        RoutingRuleEntity existing = routingRuleMapper.getById(id);
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "routing rule not found");
        RoutingRuleEntity entity = toEntity(request);
        entity.setId(id);
        entity.setStatus(request.enabled() ? "ENABLED" : "DISABLED");
        routingRuleMapper.update(entity);
        return toDto(entity);
    }

    public List<RoutingSwitchLogDto> listSwitchLogs() {
        return routingRuleMapper.listHitLogs().stream()
                .map(l -> new RoutingSwitchLogDto(l.getReason(), l.getFromTarget(), l.getToTarget()))
                .toList();
    }

    private RoutingRuleDto toDto(RoutingRuleEntity e) {
        return new RoutingRuleDto(e.getId(), e.getName(), e.getStrategyType(), e.getConditionExpr(),
                e.getTargetProviderId(), e.getTargetModelId(), e.getPriority(), "ENABLED".equals(e.getStatus()));
    }

    private RoutingRuleEntity toEntity(RoutingRuleDto d) {
        RoutingRuleEntity e = new RoutingRuleEntity();
        e.setId(d.id());
        e.setName(d.name());
        e.setStrategyType(d.strategy());
        e.setConditionExpr(d.conditionExpr());
        e.setTargetProviderId(d.targetProviderId());
        e.setTargetModelId(d.targetModelId());
        e.setPriority(d.priority());
        return e;
    }

    public record RoutingRuleDto(Long id, String name, String strategy, String conditionExpr,
                                 Long targetProviderId, Long targetModelId, int priority, boolean enabled) {}
    public record RoutingSwitchLogDto(String reason, String fromTarget, String toTarget) {}
}
