package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.notification.SendResultDto;
import org.chobit.knot.gateway.dto.notification.TemplateDto;
import org.chobit.knot.gateway.entity.NotifyTemplateEntity;
import org.chobit.knot.gateway.vo.notification.NotifySendResult;
import org.chobit.knot.gateway.vo.notification.NotifyTemplate;
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
