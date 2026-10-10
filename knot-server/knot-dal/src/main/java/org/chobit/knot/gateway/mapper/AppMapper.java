package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.AppEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppMapper {

    List<AppEntity> list(@Param("keyword") String keyword);

    AppEntity getById(Long id);

    Long countByAppCode(String appCode);

    int insert(AppEntity entity);

    int update(AppEntity entity);

    int softDelete(Long id);

    /** 按部门业务码统计应用数（kb_apps.dept_code 存业务码） */
    Long countByDeptCode(@Param("deptCode") String deptCode);
}
