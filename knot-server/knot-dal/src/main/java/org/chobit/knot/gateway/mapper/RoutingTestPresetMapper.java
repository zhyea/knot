package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.RoutingTestPresetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoutingTestPresetMapper {

    List<RoutingTestPresetEntity> listPresets(@Param("keyword") String keyword,
                                             @Param("protocolCode") String protocolCode);

    RoutingTestPresetEntity getById(Long id);

    RoutingTestPresetEntity getByCode(String code);

    int countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    int insertPreset(RoutingTestPresetEntity entity);

    int updatePreset(RoutingTestPresetEntity entity);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int deletePreset(Long id);

    List<RoutingTestPresetEntity> listActive();
}
