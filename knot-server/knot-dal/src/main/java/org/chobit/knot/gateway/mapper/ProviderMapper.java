package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.ProviderEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProviderMapper {

    List<ProviderEntity> list();

    ProviderEntity getById(Long id);

    Long countByCode(@org.apache.ibatis.annotations.Param("code") String code,
                     @org.apache.ibatis.annotations.Param("excludeId") Long excludeId);

    int insert(ProviderEntity entity);

    int update(ProviderEntity entity);
}
