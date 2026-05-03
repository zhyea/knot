package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.AlertEntity;
import org.chobit.knot.gateway.entity.SecurityPolicyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SecurityMapper {
    @Select("select id, policy_code, cast(config_json as char) as config_json, status from security_policies order by id desc")
    List<SecurityPolicyEntity> listPolicies();

    @Insert("insert into security_policies(policy_type,policy_code,config_json,status) values('DEFAULT',#{policyCode},#{configJson},#{status})")
    int insertPolicy(SecurityPolicyEntity entity);

    @Update("update security_policies set config_json=#{configJson}, status=#{status} where policy_code=#{policyCode}")
    int updatePolicy(SecurityPolicyEntity entity);

    @Select("select id, level, title, status from alerts order by id desc")
    List<AlertEntity> listAlerts();

    @Select("select count(1) from alerts where status='OPEN'")
    Integer countOpenAlerts();

    @Insert("insert into cache_records(cache_key, cache_type, status) values(#{cacheKey}, #{cacheType}, 'EVICTED') "
            + "on duplicate key update status='EVICTED', updated_at=CURRENT_TIMESTAMP")
    int upsertCacheEvict(@Param("cacheKey") String cacheKey, @Param("cacheType") String cacheType);

    @Select("select round(100.0 * sum(case when status = 'ACTIVE' then 1 else 0 end) / nullif(count(*), 0), 2) "
            + "from cache_records")
    Double cacheActiveRatioPercent();
}
