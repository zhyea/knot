package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.DiscountPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DiscountPolicyMapper {

    List<DiscountPolicyEntity> listByProviderId(Long providerId);

    DiscountPolicyEntity getById(Long id);

    int insert(DiscountPolicyEntity entity);

    int update(DiscountPolicyEntity entity);
}
