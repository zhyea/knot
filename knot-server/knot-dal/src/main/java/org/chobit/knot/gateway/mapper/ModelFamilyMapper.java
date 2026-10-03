package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ModelFamilyEntity;

import java.util.List;

/**
 * 模型族维护：限定 {@code ks_enum_configs} 的 category='model_family'。
 */
@Mapper
public interface ModelFamilyMapper {

    List<ModelFamilyEntity> list(@Param("keyword") String keyword);

    ModelFamilyEntity getById(Long id);

    ModelFamilyEntity getByCode(@Param("code") String code);
}
