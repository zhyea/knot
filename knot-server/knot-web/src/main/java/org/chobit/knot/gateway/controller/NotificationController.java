package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.NotificationConverter;
import org.chobit.knot.gateway.dto.notification.SendResultDto;
import org.chobit.knot.gateway.dto.notification.TemplateDto;
import org.chobit.knot.gateway.service.NotificationService;
import org.chobit.knot.gateway.vo.notification.*;
import jakarta.validation.Valid;
import org.chobit.knot.gateway.vo.notification.NotifyPolicy;
import org.chobit.knot.gateway.vo.notification.NotifySendRequest;
import org.chobit.knot.gateway.vo.notification.NotifySendResult;
import org.chobit.knot.gateway.vo.notification.NotifyTemplate;
import org.springframework.web.bind.annotation.*;

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
        PageResult<TemplateDto> page = notificationService.listTemplates(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(notificationConverter::toTemplateVOList));
    }

    @PostMapping("/templates")
    public ApiResponse<NotifyTemplate> createTemplate(@RequestBody @Valid NotifyTemplate request) {
        TemplateDto created = notificationService.createTemplate(
                notificationConverter.toTemplateDto(request)
        );
        return ApiResponse.ok(notificationConverter.toTemplateVO(created));
    }

    @PostMapping("/send")
    public ApiResponse<NotifySendResult> send(@RequestBody @Valid NotifySendRequest request) {
        SendResultDto sent = notificationService.send(request.templateCode(), request.receivers(), request.vars());
        return ApiResponse.ok(notificationConverter.toSendResultVO(sent));
    }

    @PostMapping("/policies")
    public ApiResponse<NotifyPolicy> createPolicy(@RequestBody @Valid NotifyPolicy request) {
        return ApiResponse.ok(request);
    }

}
