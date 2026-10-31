package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.ModelFamilyEntity;
import org.chobit.knot.svc.vo.model.ModelFamilyItem;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 模型族 Entity ↔ 对外载体。
 *
 * <p>{@code ModelFamilyEntity} 本身已经是「枚举行投影成模型域语义」的产物
 * （{@code code} / {@code name} / {@code enabled} 三个别名在 mapper 的 SQL 里就改好了），
 * 所以这里不需要任何 {@code @Mapping}——不要在 Java 侧再改名一次，两处都改会漂移。</p>
 */
@Mapper(componentModel = "spring")
public interface ModelFamilyConverter {

    ModelFamilyItem toItem(ModelFamilyEntity entity);

    List<ModelFamilyItem> toItemList(List<ModelFamilyEntity> entities);
}
