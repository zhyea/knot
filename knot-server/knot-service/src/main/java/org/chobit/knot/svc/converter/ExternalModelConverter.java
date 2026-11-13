package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.ExternalModelItemEntity;
import org.chobit.knot.dal.entity.ExternalModelSourceEntity;
import org.chobit.knot.svc.vo.model.ExternalModelItem;
import org.chobit.knot.svc.vo.model.ExternalModelSourceItem;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 外部模型源 / 条目的 Entity ↔ 对外载体。
 *
 * 两侧字段逐字同名，无 {@code @Mapping}；只做投影，不参与 enrich。
 */
@Mapper(componentModel = "spring")
public interface ExternalModelConverter {

    ExternalModelSourceItem toSourceItem(ExternalModelSourceEntity entity);

    List<ExternalModelSourceItem> toSourceItemList(List<ExternalModelSourceEntity> entities);

    ExternalModelItem toItem(ExternalModelItemEntity entity);

    List<ExternalModelItem> toItemList(List<ExternalModelItemEntity> entities);
}
