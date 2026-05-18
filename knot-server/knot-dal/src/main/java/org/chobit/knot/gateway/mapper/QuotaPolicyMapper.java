package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.QuotaPolicyEntity;

import java.util.List;

@Mapper
public interface QuotaPolicyMapper {

    QuotaPolicyEntity getById(@Param("id") Long id);

    List<QuotaPolicyEntity> listByIds(@Param("ids") List<Long> ids);

    int insert(QuotaPolicyEntity entity);

    int update(QuotaPolicyEntity entity);

    int deleteById(@Param("id") Long id);
}
