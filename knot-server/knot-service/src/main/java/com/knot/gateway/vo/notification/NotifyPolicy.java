package com.knot.gateway.vo.notification;

public record NotifyPolicy(String eventType, String dedupWindow, String escalateAfter) {
}
