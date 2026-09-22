package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProviderAccountMapper {

    List<ProviderAccountEntity> list(@Param("keyword") String keyword);

    ProviderAccountEntity getById(Long id);

    Long countByCode(@Param("code") String code,
                     @Param("excludeId") Long excludeId);

    int insert(ProviderAccountEntity entity);

    int update(ProviderAccountEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status);
}
