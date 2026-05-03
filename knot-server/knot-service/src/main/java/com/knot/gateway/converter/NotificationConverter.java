package com.knot.gateway.converter;

import com.knot.gateway.dto.notification.SendResultDto;
import com.knot.gateway.vo.notification.*;
import com.knot.gateway.dto.notification.TemplateDto;
import com.knot.gateway.entity.NotifyTemplateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface NotificationConverter {

    // ==================== Entity → DTO ====================

    @Mapping(source = "contentTpl", target = "content")
    TemplateDto toTemplateDto(NotifyTemplateEntity entity);

    List<TemplateDto> toTemplateDtoList(List<NotifyTemplateEntity> entities);

    // ==================== DTO ↔ VO ====================

    NotifyTemplate toTemplateVO(TemplateDto dto);

    TemplateDto toTemplateDto(NotifyTemplate vo);

    List<NotifyTemplate> toTemplateVOList(List<TemplateDto> dtos);

    NotifySendResult toSendResultVO(SendResultDto dto);
}
