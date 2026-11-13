package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.ModelFamilyEntity;
import org.chobit.knot.svc.vo.model.ModelFamilyItem;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 模型族 Entity ↔ 对外载体。
 *
 * {@code ModelFamilyEntity} 已把枚举行投影成模型域语义（{@code code} / {@code name} / {@code enabled}
 * 别名在 mapper 的 SQL 里改好），故无需 {@code @Mapping}。
 */
@Mapper(componentModel = "spring")
public interface ModelFamilyConverter {

    ModelFamilyItem toItem(ModelFamilyEntity entity);

    List<ModelFamilyItem> toItemList(List<ModelFamilyEntity> entities);
}
