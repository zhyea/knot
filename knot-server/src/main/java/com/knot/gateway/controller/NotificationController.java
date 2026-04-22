package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/templates")
    public ApiResponse<List<NotifyTemplate>> templates() {
        List<NotifyTemplate> result = notificationService.listTemplates().stream()
                .map(t -> new NotifyTemplate(t.id(), t.code(), t.name(), t.channel(), t.content()))
                .toList();
        return ApiResponse.ok(result);
    }

    @PostMapping("/templates")
    public ApiResponse<NotifyTemplate> createTemplate(@RequestBody NotifyTemplate request) {
        NotificationService.TemplateDto created = notificationService.createTemplate(
                new NotificationService.TemplateDto(
                        null,
                        request.code(),
                        request.name(),
                        request.channel(),
                        request.content()
                )
        );
        return ApiResponse.ok(new NotifyTemplate(created.id(), created.code(), created.name(), created.channel(), created.content()));
    }

    @PostMapping("/send")
    public ApiResponse<NotifySendResult> send(@RequestBody NotifySendRequest request) {
        NotificationService.SendResultDto sent = notificationService.send(request.templateCode(), request.receivers(), request.vars());
        return ApiResponse.ok(new NotifySendResult(sent.taskId(), sent.status(), sent.receiverCount()));
    }

    @PostMapping("/policies")
    public ApiResponse<NotifyPolicy> createPolicy(@RequestBody NotifyPolicy request) {
        return ApiResponse.ok(request);
    }

    public record NotifyTemplate(Long id, String code, String name, String channel, String content) {
    }

    public record NotifySendRequest(String templateCode, List<String> receivers, Map<String, String> vars) {
    }

    public record NotifySendResult(String taskId, String status, int receiverCount) {
    }

    public record NotifyPolicy(String eventType, String dedupWindow, String escalateAfter) {
    }
}
