package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.vo.model.ModelItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface ModelConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(source = "isDeleted", target = "deleted", qualifiedByName = "deletedFlag")
    @Mapping(target = "rateLimitPolicy", ignore = true)
    @Mapping(target = "quotaPolicy", ignore = true)
    @Mapping(target = "apiBindings", ignore = true)
    ModelDto toDto(ModelEntity entity);

    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(target = "providerName", ignore = true)
    @Mapping(target = "providerCode", ignore = true)
    // modelType 派生自绑定的统一模型，不允许从请求写回；logicalModelCode 来自请求，
    // 但只经 kb_provider_model_mappings 落库，不写 kb_models 列；billingRuleCode 来自请求（按业务码绑定）
    @Mapping(target = "modelType", ignore = true)
    @Mapping(target = "logicalModelCode", ignore = true)
    // modelFamilyCode 也是派生字段（取自 kb_logical_models.model_family），不落 kb_models 列
    @Mapping(target = "modelFamilyCode", ignore = true)
    // is_deleted 只能通过删除 / 恢复接口翻转，不接受请求写回
    @Mapping(target = "isDeleted", ignore = true)
    ModelEntity toEntity(ModelDto dto);

    List<ModelDto> toDtoList(List<ModelEntity> entities);

    // ==================== DTO ↔ VO ====================

    ModelItem toVO(ModelDto dto);

    @Mapping(target = "providerName", ignore = true)
    ModelDto toDto(ModelItem vo);

    List<ModelItem> toVOList(List<ModelDto> dtos);
}
