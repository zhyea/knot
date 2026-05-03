package org.chobit.knot.gateway.dto.notification;

public record TemplateDto(Long id, String code, String name, String channel, String content) {
}
