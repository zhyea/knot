package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.DiscountPolicyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiscountPolicyMapper {

    List<DiscountPolicyEntity> listByModelCode(@Param("modelCode") String modelCode);

    DiscountPolicyEntity getById(Long id);

    /** 按模型业务码统计折扣策略数（用于模型删除拦截） */
    Long countByModelCode(@Param("modelCode") String modelCode);

    int insert(DiscountPolicyEntity entity);

    int update(DiscountPolicyEntity entity);
}
