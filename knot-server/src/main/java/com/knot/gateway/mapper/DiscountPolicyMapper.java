package com.knot.gateway.mapper;

import com.knot.gateway.entity.DiscountPolicyEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DiscountPolicyMapper {
    @Select("select id, provider_id, policy_name, scope_type, scope_ref_id, discount_type, discount_value, priority, effective_from, effective_to, status, remark from provider_discount_policies where provider_id=#{providerId} order by priority asc, id desc")
    List<DiscountPolicyEntity> listByProviderId(Long providerId);

    @Select("select id, provider_id, policy_name, scope_type, scope_ref_id, discount_type, discount_value, priority, effective_from, effective_to, status, remark from provider_discount_policies where id=#{id}")
    DiscountPolicyEntity getById(Long id);

    @Insert("insert into provider_discount_policies(provider_id, policy_name, scope_type, scope_ref_id, discount_type, discount_value, priority, effective_from, status) values(#{providerId}, #{policyName}, #{scopeType}, #{scopeRefId}, #{discountType}, #{discountValue}, #{priority}, #{effectiveFrom}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DiscountPolicyEntity entity);

    @Update("update provider_discount_policies set policy_name=#{policyName}, scope_type=#{scopeType}, scope_ref_id=#{scopeRefId}, discount_type=#{discountType}, discount_value=#{discountValue}, priority=#{priority}, status=#{status} where id=#{id}")
    int update(DiscountPolicyEntity entity);
}
