package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ProviderProfileEntity;

import java.util.List;

@Mapper
public interface ProviderProfileMapper {

    List<ProviderProfileEntity> list(@Param("keyword") String keyword,
                                     @Param("tag") String tag);

    ProviderProfileEntity getById(Long id);

    Long countByCode(@Param("code") String code,
                     @Param("excludeId") Long excludeId);

    Long countAccountsByProviderId(Long providerId);

    Long countCredentialsByProviderId(Long providerId);

    Long countDiscountPoliciesByProviderId(Long providerId);

    Long countModelsByProviderId(Long providerId);

    Long countMappingsByProviderId(Long providerId);

    Long countBillingRulesByProviderId(Long providerId);

    int insert(ProviderProfileEntity entity);

    int update(ProviderProfileEntity entity);

    int deleteById(Long id);
}
