package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.notification.TemplateDto;
import org.chobit.knot.dal.entity.NotifyTemplateEntity;
import org.chobit.knot.svc.vo.notification.NotifyTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = SharedMappings.class)
public interface NotificationConverter {

    // ==================== Entity → DTO ====================

    @Mapping(source = "contentTpl", target = "content")
    TemplateDto toTemplateDto(NotifyTemplateEntity entity);

    List<TemplateDto> toTemplateDtoList(List<NotifyTemplateEntity> entities);

    // ==================== DTO ↔ VO ====================

    NotifyTemplate toTemplateVO(TemplateDto dto);

    TemplateDto toTemplateDto(NotifyTemplate vo);

    List<NotifyTemplate> toTemplateVOList(List<TemplateDto> dtos);
}
