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
 * <p>⚠ {@link EnumCategorySummary} 虽然叫 Summary，却住在 {@code dal.entity} 且是 MyBatis 的
 * {@code resultType}（聚合投影，不是表映射）。它必须留在 dal（MyBatis 要能 new 它），
 * 但不能再直接当 HTTP 响应体——所以这里多一层转换。这不是纯拷贝壳：
 * 它把「dal 的查询投影」和「管理面契约」这两个会各自演化的东西分开了。</p>
 */
@Mapper(componentModel = "spring")
public interface EnumConfigConverter {

    EnumConfigItem toItem(EnumConfigEntity entity);

    List<EnumConfigItem> toItemList(List<EnumConfigEntity> entities);

    EnumConfigEntity toEntity(EnumConfigItem item);

    EnumCategorySummaryItem toSummaryItem(EnumCategorySummary summary);

    List<EnumCategorySummaryItem> toSummaryItemList(List<EnumCategorySummary> summaries);
}
