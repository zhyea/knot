package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.DepartmentEntity;

import java.util.List;

@Mapper
public interface DepartmentMapper {

    List<DepartmentEntity> list(@Param("keyword") String keyword, @Param("parentId") Long parentId);

    List<DepartmentEntity> listAll();

    DepartmentEntity getById(Long id);

    DepartmentEntity getByCode(String deptCode);

    Long countByParentId(Long parentId);

    int insert(DepartmentEntity entity);

    int update(DepartmentEntity entity);

    int updateStatus(DepartmentEntity entity);

    int softDelete(Long id);
}
