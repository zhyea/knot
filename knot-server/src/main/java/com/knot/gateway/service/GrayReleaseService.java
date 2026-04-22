package com.knot.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.entity.GrayPlanEntity;
import com.knot.gateway.mapper.GrayPlanMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GrayReleaseService {
    private static final List<Integer> DEFAULT_STEPS = List.of(10, 30, 50, 100);

    private final GrayPlanMapper grayPlanMapper;
    private final ObjectMapper objectMapper;

    public GrayReleaseService(GrayPlanMapper grayPlanMapper, ObjectMapper objectMapper) {
        this.grayPlanMapper = grayPlanMapper;
        this.objectMapper = objectMapper;
    }

    public GrayPlanDto create(GrayPlanDto request) {
        List<Integer> steps = normalizeSteps(request.steps());
        GrayPlanEntity e = new GrayPlanEntity();
        e.setPlanName("gray_plan_" + System.currentTimeMillis());
        e.setTargetType(request.targetType());
        e.setTargetId(request.targetId());
        e.setTrafficPercent(request.trafficPercent() != null ? request.trafficPercent() : 10);
        e.setStepsJson(writeStepsJson(steps));
        e.setStartTime(LocalDateTime.now());
        e.setStatus("DRAFT");
        grayPlanMapper.insert(e);
        return toDto(e, steps);
    }

    public GrayPlanDto publish(Long id) {
        GrayPlanEntity e = grayPlanMapper.getById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "gray plan not found");
        }
        e.setStatus("RUNNING");
        grayPlanMapper.updateStatus(e);
        return toDto(grayPlanMapper.getById(id));
    }

    public GrayPlanDto rollback(Long id) {
        GrayPlanEntity e = grayPlanMapper.getById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "gray plan not found");
        }
        e.setStatus("ROLLED_BACK");
        grayPlanMapper.updateStatus(e);
        return toDto(grayPlanMapper.getById(id));
    }

    public List<GrayPlanDto> list() {
        return grayPlanMapper.list().stream().map(this::toDto).toList();
    }

    private GrayPlanDto toDto(GrayPlanEntity e) {
        return toDto(e, readSteps(e.getStepsJson()));
    }

    private GrayPlanDto toDto(GrayPlanEntity e, List<Integer> steps) {
        return new GrayPlanDto(e.getId(), e.getTargetType(), e.getTargetId(), steps, e.getTrafficPercent(), e.getStatus());
    }

    private List<Integer> normalizeSteps(List<Integer> steps) {
        if (steps == null || steps.isEmpty()) {
            return DEFAULT_STEPS;
        }
        return List.copyOf(steps);
    }

    private List<Integer> readSteps(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT_STEPS;
        }
        try {
            List<Integer> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null || parsed.isEmpty() ? DEFAULT_STEPS : List.copyOf(parsed);
        } catch (JsonProcessingException ex) {
            return DEFAULT_STEPS;
        }
    }

    private String writeStepsJson(List<Integer> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "steps json serialize failed");
        }
    }

    public record GrayPlanDto(Long id, String targetType, Long targetId, List<Integer> steps, Integer trafficPercent,
                              String status) {
    }
}
