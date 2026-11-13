package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.ExternalModelItemEntity;
import org.chobit.knot.dal.entity.ExternalModelSourceEntity;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExternalModelMapper {

    List<ExternalModelSourceEntity> listSources();

    ExternalModelSourceEntity getSourceByCode(String sourceCode);

    int insertSource(ExternalModelSourceEntity entity);

    int updateSourceLastSyncAt(@Param("sourceCode") String sourceCode, @Param("lastSyncAt") LocalDateTime lastSyncAt);

    List<ExternalModelItemEntity> listItems(@Param("sourceCode") String sourceCode,
                                            @Param("syncStatus") Integer syncStatus,
                                            @Param("keyword") String keyword,
                                            @Param("modelType") String modelType,
                                            @Param("ids") List<Long> ids);

    ExternalModelItemEntity getItemById(Long id);

    /**
     * 按 {@code model_id} 查条目。同步去重只认模型 ID：同一模型跨源视为同一条目，
     * 最近同步的源覆盖旧数据并接管归属；唯一键 {@code uk_external_model_item} 为 (model_id)。
     */
    ExternalModelItemEntity getItemByModelId(@Param("modelId") String modelId);

    int insertItem(ExternalModelItemEntity entity);

    int updateItem(ExternalModelItemEntity entity);

    int deleteItem(Long id);

    int deleteItems(@Param("ids") List<Long> ids);

    int updateItemLogicalModel(@Param("id") Long id,
                               @Param("logicalModelId") Long logicalModelId);

    int clearLogicalModelMatch(Long logicalModelId);

    int updateItemIgnored(@Param("id") Long id, @Param("ignored") Boolean ignored);

    Long countByLogicalModelId(@Param("logicalModelId") Long logicalModelId);
}
