package org.chobit.knot.dal.mapper;

import org.chobit.knot.dal.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperationLogMapper {

    int insert(OperationLogEntity entity);

    List<OperationLogEntity> list(@Param("module") String module,
                                  @Param("operation") String operation,
                                  @Param("status") Integer status,
                                  @Param("keyword") String keyword,
                                  @Param("entityType") String entityType,
                                  @Param("entityId") Long entityId,
                                  @Param("entityNamePrefix") String entityNamePrefix);

    List<String> listDistinctModules();

    List<String> listDistinctOperations();

    OperationLogEntity getById(Long id);

    int deleteByCreatedBefore(LocalDateTime beforeTime);

    /** 最近操作日志（总览看板用）：无条件倒序取全量，由调用方截断 */
    List<OperationLogEntity> listOperationLogs();
}
