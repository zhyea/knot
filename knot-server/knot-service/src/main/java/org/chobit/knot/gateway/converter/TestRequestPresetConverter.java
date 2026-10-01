package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.routing.TestRequestPresetDto;
import org.chobit.knot.gateway.entity.TestRequestPresetEntity;
import org.chobit.knot.gateway.vo.routing.TestRequestPreset;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface TestRequestPresetConverter {

    TestRequestPresetDto toDto(TestRequestPresetEntity entity);

    List<TestRequestPresetDto> toDtoList(List<TestRequestPresetEntity> entities);

    TestRequestPreset toVO(TestRequestPresetDto dto);

    TestRequestPresetDto toDto(TestRequestPreset vo);

    List<TestRequestPreset> toVOList(List<TestRequestPresetDto> dtos);
}
