package com.knot.gateway.vo.grayrelease;

import java.util.List;

public record GrayPlanRequest(String targetType, Long targetId, List<Integer> steps, Integer trafficPercent) {
}
