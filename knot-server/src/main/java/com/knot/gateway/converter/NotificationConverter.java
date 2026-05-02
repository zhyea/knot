package com.knot.gateway.converter;

import com.knot.gateway.controller.NotificationController;
import com.knot.gateway.entity.NotifyTemplateEntity;
import com.knot.gateway.service.NotificationService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface NotificationConverter {

    // ==================== Entity → DTO ====================

    @Mapping(source = "contentTpl", target = "content")
    NotificationService.TemplateDto toTemplateDto(NotifyTemplateEntity entity);

    List<NotificationService.TemplateDto> toTemplateDtoList(List<NotifyTemplateEntity> entities);

    // ==================== DTO ↔ VO ====================

    NotificationController.NotifyTemplate toTemplateVO(NotificationService.TemplateDto dto);

    NotificationService.TemplateDto toTemplateDto(NotificationController.NotifyTemplate vo);

    List<NotificationController.NotifyTemplate> toTemplateVOList(List<NotificationService.TemplateDto> dtos);

    NotificationController.NotifySendResult toSendResultVO(NotificationService.SendResultDto dto);
}
