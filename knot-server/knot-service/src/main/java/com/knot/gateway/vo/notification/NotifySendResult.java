package com.knot.gateway.vo.notification;

public record NotifySendResult(String taskId, String status, int receiverCount) {
}
