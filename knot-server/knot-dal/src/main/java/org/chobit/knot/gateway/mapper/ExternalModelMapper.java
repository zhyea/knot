package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ExternalModelItemEntity;
import org.chobit.knot.gateway.entity.ExternalModelSourceEntity;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExternalModelMapper {

    List<ExternalModelSourceEntity> listSources();

    ExternalModelSourceEntity getSourceByCode(String sourceCode);

    int insertSource(ExternalModelSourceEntity entity);

    int updateSourceLastSyncAt(@Param("sourceCode") String sourceCode, @Param("lastSyncAt") LocalDateTime lastSyncAt);

    List<ExternalModelItemEntity> listItems(@Param("sourceCode") String sourceCode,
                                            @Param("syncStatus") String syncStatus,
                                            @Param("keyword") String keyword,
                                            @Param("modelType") String modelType);

    ExternalModelItemEntity getItemById(Long id);

    ExternalModelItemEntity getItemBySourceKey(@Param("sourceCode") String sourceCode,
                                               @Param("modelId") String modelId);

    int insertItem(ExternalModelItemEntity entity);

    int updateItem(ExternalModelItemEntity entity);

    int deleteItem(Long id);

    int deleteItems(@Param("ids") List<Long> ids);

    int updateItemLogicalModel(@Param("id") Long id,
                               @Param("logicalModelId") Long logicalModelId);

    int clearLogicalModelMatch(Long logicalModelId);
}
