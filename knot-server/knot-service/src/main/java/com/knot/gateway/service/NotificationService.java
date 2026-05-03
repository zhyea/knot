package com.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.NotificationConverter;
import com.knot.gateway.dto.notification.SendResultDto;
import com.knot.gateway.dto.notification.TemplateDto;
import com.knot.gateway.entity.NotifyTemplateEntity;
import com.knot.gateway.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final NotificationConverter notificationConverter;

    public NotificationService(NotificationMapper notificationMapper, NotificationConverter notificationConverter) {
        this.notificationMapper = notificationMapper;
        this.notificationConverter = notificationConverter;
    }

    public PageResult<TemplateDto> listTemplates(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<NotifyTemplateEntity> pageInfo = new PageInfo<>(notificationMapper.listTemplates());
        return PageResult.fromPage(pageInfo, notificationConverter::toTemplateDtoList, pageRequest);
    }

    @Transactional
    public TemplateDto createTemplate(TemplateDto request) {
        NotifyTemplateEntity e = new NotifyTemplateEntity();
        e.setCode(request.code());
        e.setName(request.name());
        e.setChannel(request.channel());
        e.setContentTpl(request.content());
        e.setStatus("ACTIVE");
        notificationMapper.insertTemplate(e);
        return notificationConverter.toTemplateDto(e);
    }

    @Transactional
    public SendResultDto send(String templateCode, List<String> receivers, Map<String, String> vars) {
        NotifyTemplateEntity entity = notificationMapper.getTemplateByCode(templateCode);
        TemplateDto template = entity != null
                ? notificationConverter.toTemplateDto(entity)
                : new TemplateDto(0L, templateCode, templateCode, "EMAIL", "");
        for (String receiver : receivers) {
            notificationMapper.insertRecord(template.id(), receiver, template.channel(), "SENT");
        }
        return new SendResultDto("ntf_" + System.currentTimeMillis(), "SENT", receivers.size());
    }

}
