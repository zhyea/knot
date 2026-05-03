package com.knot.gateway.vo.grayrelease;

import java.util.List;

public record GrayPlan(Long id, String targetType, Long targetId, List<Integer> steps, Integer trafficPercent,
                       String status) {
}
