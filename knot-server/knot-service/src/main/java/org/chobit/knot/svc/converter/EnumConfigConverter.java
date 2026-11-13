package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.EnumCategorySummary;
import org.chobit.knot.dal.entity.EnumConfigEntity;
import org.chobit.knot.svc.vo.system.EnumCategorySummaryItem;
import org.chobit.knot.svc.vo.system.EnumConfigItem;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 枚举项 / 枚举分类聚合的 Entity ↔ 对外载体。
 *
 * {@link EnumCategorySummary} 是 {@code dal.entity} 里的 MyBatis {@code resultType}（聚合投影，非表映射），
 * 必须留在 dal，但不直接作为 HTTP 响应体。
 */
@Mapper(componentModel = "spring")
public interface EnumConfigConverter {

    EnumConfigItem toItem(EnumConfigEntity entity);

    List<EnumConfigItem> toItemList(List<EnumConfigEntity> entities);

    EnumConfigEntity toEntity(EnumConfigItem item);

    EnumCategorySummaryItem toSummaryItem(EnumCategorySummary summary);

    List<EnumCategorySummaryItem> toSummaryItemList(List<EnumCategorySummary> summaries);
}
