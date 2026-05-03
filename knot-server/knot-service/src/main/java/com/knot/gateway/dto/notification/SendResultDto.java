package com.knot.gateway.dto.notification;

public record SendResultDto(String taskId, String status, int receiverCount) {
}
