package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.converter.NotificationConverter;
import org.chobit.knot.svc.dto.notification.TemplateDto;
import org.chobit.knot.svc.service.NotificationService;
import org.chobit.knot.svc.vo.notification.NotifyTemplate;
import jakarta.validation.Valid;
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
    @OperationLog(module = "notification", operation = "CREATE", entityType = "NotifyTemplate",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'新建通知模板'",
            newValueSpel = "#result")
    @PostMapping("/templates")
    public NotifyTemplate createTemplate(@RequestBody @Valid NotifyTemplate request) {
        TemplateDto created = notificationService.createTemplate(
                notificationConverter.toTemplateDto(request)
        );
        return notificationConverter.toTemplateVO(created);
    }

}
