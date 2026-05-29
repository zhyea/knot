package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.ProviderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProviderMapper {

    List<ProviderEntity> list(@Param("keyword") String keyword);

    ProviderEntity getById(Long id);

    Long countByCode(@Param("code") String code,
                     @Param("excludeId") Long excludeId);

    int insert(ProviderEntity entity);

    int update(ProviderEntity entity);
}
