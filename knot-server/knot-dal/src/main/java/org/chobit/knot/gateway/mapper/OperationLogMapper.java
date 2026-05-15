package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OperationLogMapper {

    int insert(OperationLogEntity entity);

    List<OperationLogEntity> listByModule(@Param("module") String module,
                                          @Param("entityType") String entityType,
                                          @Param("entityId") Long entityId,
                                          @Param("operatorId") Long operatorId);

    /**
     * 枚举分类相关操作日志：entity_name 为「分类」或「分类/编码」
     */
    List<OperationLogEntity> listByModuleAndEntityNamePrefix(@Param("module") String module,
                                                             @Param("entityNamePrefix") String entityNamePrefix);

    OperationLogEntity getById(Long id);
}
