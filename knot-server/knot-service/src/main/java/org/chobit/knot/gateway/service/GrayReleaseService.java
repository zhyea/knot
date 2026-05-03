package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.CommonMappings;
import org.chobit.knot.gateway.converter.GrayReleaseConverter;
import org.chobit.knot.gateway.dto.grayrelease.GrayPlanDto;
import org.chobit.knot.gateway.entity.GrayPlanEntity;
import org.chobit.knot.gateway.mapper.GrayPlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GrayReleaseService {
    private static final List<Integer> DEFAULT_STEPS = List.of(10, 30, 50, 100);

    private final GrayPlanMapper grayPlanMapper;
    private final GrayReleaseConverter grayReleaseConverter;
    private final CommonMappings commonMappings;

    public GrayReleaseService(GrayPlanMapper grayPlanMapper, GrayReleaseConverter grayReleaseConverter, CommonMappings commonMappings) {
        this.grayPlanMapper = grayPlanMapper;
        this.grayReleaseConverter = grayReleaseConverter;
        this.commonMappings = commonMappings;
    }

    @Transactional
    public GrayPlanDto create(GrayPlanDto request) {
        List<Integer> steps = normalizeSteps(request.steps());
        GrayPlanEntity e = new GrayPlanEntity();
        e.setPlanName("gray_plan_" + System.currentTimeMillis());
        e.setTargetType(request.targetType());
        e.setTargetId(request.targetId());
        e.setTrafficPercent(request.trafficPercent() != null ? request.trafficPercent() : 10);
        e.setStepsJson(commonMappings.stepsToJson(steps));
        e.setStartTime(LocalDateTime.now());
        e.setStatus("DRAFT");
        grayPlanMapper.insert(e);
        return grayReleaseConverter.toDto(e);
    }

    @Transactional
    public GrayPlanDto publish(Long id) {
        GrayPlanEntity e = grayPlanMapper.getById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "gray plan not found");
        }
        e.setStatus("RUNNING");
        grayPlanMapper.updateStatus(e);
        return grayReleaseConverter.toDto(grayPlanMapper.getById(id));
    }

    @Transactional
    public GrayPlanDto rollback(Long id) {
        GrayPlanEntity e = grayPlanMapper.getById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "gray plan not found");
        }
        e.setStatus("ROLLED_BACK");
        grayPlanMapper.updateStatus(e);
        return grayReleaseConverter.toDto(grayPlanMapper.getById(id));
    }

    public PageResult<GrayPlanDto> list(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<GrayPlanEntity> pageInfo = new PageInfo<>(grayPlanMapper.list());
        return PageResult.fromPage(pageInfo, grayReleaseConverter::toDtoList, pageRequest);
    }

    private List<Integer> normalizeSteps(List<Integer> steps) {
        if (steps == null || steps.isEmpty()) {
            return DEFAULT_STEPS;
        }
        return List.copyOf(steps);
    }

}
