package com.knot.gateway.dto.grayrelease;

import java.util.List;

public record GrayPlanDto(Long id, String targetType, Long targetId, List<Integer> steps, Integer trafficPercent,
                          String status) {
}
