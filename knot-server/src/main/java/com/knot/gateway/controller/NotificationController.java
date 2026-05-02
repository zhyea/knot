package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.NotificationConverter;
import com.knot.gateway.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationConverter notificationConverter;

    public NotificationController(NotificationService notificationService, NotificationConverter notificationConverter) {
        this.notificationService = notificationService;
        this.notificationConverter = notificationConverter;
    }

    @GetMapping("/templates")
    public ApiResponse<PageResult<NotifyTemplate>> templates(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<NotificationService.TemplateDto> page = notificationService.listTemplates(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(notificationConverter::toTemplateVOList));
    }

    @PostMapping("/templates")
    public ApiResponse<NotifyTemplate> createTemplate(@RequestBody @Valid NotifyTemplate request) {
        NotificationService.TemplateDto created = notificationService.createTemplate(
                notificationConverter.toTemplateDto(request)
        );
        return ApiResponse.ok(notificationConverter.toTemplateVO(created));
    }

    @PostMapping("/send")
    public ApiResponse<NotifySendResult> send(@RequestBody @Valid NotifySendRequest request) {
        NotificationService.SendResultDto sent = notificationService.send(request.templateCode(), request.receivers(), request.vars());
        return ApiResponse.ok(notificationConverter.toSendResultVO(sent));
    }

    @PostMapping("/policies")
    public ApiResponse<NotifyPolicy> createPolicy(@RequestBody @Valid NotifyPolicy request) {
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
