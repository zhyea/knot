package com.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.RoutingRuleConverter;
import com.knot.gateway.dto.routing.RoutingRuleDto;
import com.knot.gateway.dto.routing.RoutingSwitchLogDto;
import com.knot.gateway.entity.RoutingHitLogEntity;
import com.knot.gateway.entity.RoutingRuleEntity;
import com.knot.gateway.mapper.RoutingRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoutingRuleService {
    private final RoutingRuleMapper routingRuleMapper;
    private final RoutingRuleConverter routingRuleConverter;

    public RoutingRuleService(RoutingRuleMapper routingRuleMapper, RoutingRuleConverter routingRuleConverter) {
        this.routingRuleMapper = routingRuleMapper;
        this.routingRuleConverter = routingRuleConverter;
    }

    public PageResult<RoutingRuleDto> list(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<RoutingRuleEntity> pageInfo = new PageInfo<>(routingRuleMapper.list());
        return PageResult.fromPage(pageInfo, routingRuleConverter::toDtoList, pageRequest);
    }

    public RoutingRuleDto getById(Long id) {
        RoutingRuleEntity entity = routingRuleMapper.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "routing rule not found");
        return routingRuleConverter.toDto(entity);
    }

    @Transactional
    public RoutingRuleDto create(RoutingRuleDto request) {
        RoutingRuleEntity entity = routingRuleConverter.toEntity(request);
        entity.setStatus(request.enabled() ? "ENABLED" : "DISABLED");
        routingRuleMapper.insert(entity);
        return routingRuleConverter.toDto(entity);
    }

    @Transactional
    public RoutingRuleDto update(Long id, RoutingRuleDto request) {
        RoutingRuleEntity existing = routingRuleMapper.getById(id);
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "routing rule not found");
        RoutingRuleEntity entity = routingRuleConverter.toEntity(request);
        entity.setId(id);
        entity.setStatus(request.enabled() ? "ENABLED" : "DISABLED");
        routingRuleMapper.update(entity);
        return routingRuleConverter.toDto(entity);
    }

    public PageResult<RoutingSwitchLogDto> listSwitchLogs(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<RoutingHitLogEntity> pageInfo = new PageInfo<>(routingRuleMapper.listHitLogs());
        return PageResult.fromPage(pageInfo, routingRuleConverter::toSwitchLogDtoList, pageRequest);
    }

}
