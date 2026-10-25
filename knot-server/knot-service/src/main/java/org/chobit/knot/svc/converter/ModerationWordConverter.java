package org.chobit.knot.svc.converter;

import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.svc.dto.moderation.ModerationWordDto;
import org.chobit.knot.dal.entity.ModerationWordEntity;
import org.chobit.knot.svc.vo.moderation.ModerationWordItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 敏感词条 Entity / DTO / VO 互转。
 *
 * <p>{@code normalizedWord} 只在 DTO 与 Entity 之间流转：VO 不暴露它，前端只维护原文。</p>
 */
@Component
public class ModerationWordConverter {

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public ModerationWordDto toDto(ModerationWordEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ModerationWordDto(
                entity.getId(),
                entity.getWord(),
                entity.getCategoryCode(),
                entity.getSeverity(),
                entity.getLang(),
                EnabledStatusEnum.isEnabled(entity.getStatus()),
                entity.getIsDeleted() != null && entity.getIsDeleted() != 0,
                entity.getRemark(),
                entity.getNormalizedWord(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public ModerationWordItem toVO(ModerationWordDto dto) {
        if (dto == null) {
            return null;
        }
        return new ModerationWordItem(
                dto.id(),
                dto.word(),
                dto.categoryCode(),
                dto.severity(),
                dto.lang(),
                dto.enabled(),
                dto.deleted(),
                dto.remark(),
                dto.createdAt(),
                dto.updatedAt()
        );
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public ModerationWordEntity toEntity(ModerationWordDto dto) {
        if (dto == null) {
            return null;
        }
        ModerationWordEntity entity = new ModerationWordEntity();
        entity.setId(dto.id());
        entity.setWord(dto.word());
        entity.setNormalizedWord(dto.normalizedWord());
        entity.setCategoryCode(dto.categoryCode());
        entity.setSeverity(dto.severity());
        entity.setLang(dto.lang());
        entity.setStatus(EnabledStatusEnum.codeOf(dto.enabled()));
        entity.setRemark(dto.remark());
        return entity;
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public List<ModerationWordItem> toVOList(List<ModerationWordDto> list) {
        return list == null ? List.of() : list.stream().map(this::toVO).toList();
    }
}
