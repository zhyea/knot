package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.AlertEntity;
import org.chobit.knot.gateway.entity.SecurityPolicyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SecurityMapper {

    List<SecurityPolicyEntity> listPolicies();

    int insertPolicy(SecurityPolicyEntity entity);

    int updatePolicy(SecurityPolicyEntity entity);

    List<AlertEntity> listAlerts();

    Integer countOpenAlerts();

    int upsertCacheEvict(@Param("cacheKey") String cacheKey, @Param("cacheType") String cacheType);

    Double cacheActiveRatioPercent();
}
