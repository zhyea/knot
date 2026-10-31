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
 * <p>两侧字段逐字同名，所以没有任何 {@code @Mapping}——别在这里顺手改名或做「友好化」，
 * 那会让前端已有的字段读取路径和这里的映射各说一套。</p>
 *
 * <p>⚠ 只做投影，不参与 enrich：条目的 {@code rawJson} / {@code description} 是
 * {@code ExternalModelService} 从供应商回捞后<b>先落库再投影</b>的，转换器拿到什么就是什么。</p>
 */
@Mapper(componentModel = "spring")
public interface ExternalModelConverter {

    ExternalModelSourceItem toSourceItem(ExternalModelSourceEntity entity);

    List<ExternalModelSourceItem> toSourceItemList(List<ExternalModelSourceEntity> entities);

    ExternalModelItem toItem(ExternalModelItemEntity entity);

    List<ExternalModelItem> toItemList(List<ExternalModelItemEntity> entities);
}
