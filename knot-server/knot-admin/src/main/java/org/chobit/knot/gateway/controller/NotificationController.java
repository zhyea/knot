package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.model.PageQuery;
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

    /**
     * Constructs a new instance.
     */
    public NotificationController(NotificationService notificationService, NotificationConverter notificationConverter) {
        this.notificationService = notificationService;
        this.notificationConverter = notificationConverter;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/templates/list")
    public PageResult<NotifyTemplate> templates(@RequestBody(required = false) PageQuery query) {
        PageResult<TemplateDto> page = notificationService.listTemplates(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword()
        );
        return page.mapList(notificationConverter::toTemplateVOList);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @PostMapping("/templates")
    public NotifyTemplate createTemplate(@RequestBody @Valid NotifyTemplate request) {
        TemplateDto created = notificationService.createTemplate(
                notificationConverter.toTemplateDto(request)
        );
        return notificationConverter.toTemplateVO(created);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/send")
    public NotifySendResult send(@RequestBody @Valid NotifySendRequest request) {
        SendResultDto sent = notificationService.send(request.templateCode(), request.receivers(), request.vars());
        return notificationConverter.toSendResultVO(sent);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @PostMapping("/policies")
    public NotifyPolicy createPolicy(@RequestBody @Valid NotifyPolicy request) {
        return request;
    }

}
