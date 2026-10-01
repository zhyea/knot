package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.TestRequestPresetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TestRequestPresetMapper {

    List<TestRequestPresetEntity> listPresets(@Param("keyword") String keyword,
                                             @Param("protocolCode") String protocolCode);

    TestRequestPresetEntity getById(Long id);

    TestRequestPresetEntity getByCode(String code);

    int countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    int insertPreset(TestRequestPresetEntity entity);

    int updatePreset(TestRequestPresetEntity entity);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int deletePreset(Long id);

    List<TestRequestPresetEntity> listActive();
}
