package com.knot.gateway.vo.notification;

import java.util.List;
import java.util.Map;

public record NotifySendRequest(String templateCode, List<String> receivers, Map<String, String> vars) {
}
