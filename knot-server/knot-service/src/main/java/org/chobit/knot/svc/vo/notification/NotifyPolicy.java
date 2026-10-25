package org.chobit.knot.svc.vo.notification;

public record NotifyPolicy(String eventType, String dedupWindow, String escalateAfter) {
}
