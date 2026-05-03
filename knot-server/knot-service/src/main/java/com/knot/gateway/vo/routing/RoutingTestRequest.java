package com.knot.gateway.vo.routing;

import java.util.List;

public record RoutingTestRequest(String appId, List<String> tags, String time) {
}
