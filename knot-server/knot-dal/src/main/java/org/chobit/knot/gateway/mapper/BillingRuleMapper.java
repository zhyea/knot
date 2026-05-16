package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BillingRuleMapper {

    List<BillingRuleEntity> list();

    BillingRuleEntity getById(Long id);

    int insert(BillingRuleEntity entity);

    int update(BillingRuleEntity entity);
}
