package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.NotificationConverter;
import org.chobit.knot.gateway.dto.notification.TemplateDto;
import org.chobit.knot.gateway.entity.NotifyTemplateEntity;
import org.chobit.knot.gateway.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final NotificationConverter notificationConverter;

    /**
     * Constructs a new instance.
     */
    public NotificationService(NotificationMapper notificationMapper, NotificationConverter notificationConverter) {
        this.notificationMapper = notificationMapper;
        this.notificationConverter = notificationConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<TemplateDto> listTemplates(PageRequest pageRequest) {
        return listTemplates(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<TemplateDto> listTemplates(PageRequest pageRequest, String keyword) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<NotifyTemplateEntity> pageInfo = new PageInfo<>(notificationMapper.listTemplates(normalizeKeyword(keyword)));
            return PageResult.fromPage(pageInfo, notificationConverter::toTemplateDtoList, pageRequest);
        }
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public TemplateDto createTemplate(TemplateDto request) {
        NotifyTemplateEntity e = new NotifyTemplateEntity();
        e.setCode(request.code());
        e.setName(request.name());
        e.setChannel(request.channel());
        e.setContentTpl(request.content());
        e.setStatus(EnabledStatusEnum.ENABLED.code());
        notificationMapper.insertTemplate(e);
        return notificationConverter.toTemplateDto(e);
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        return value.isEmpty() ? null : value;
    }
}
