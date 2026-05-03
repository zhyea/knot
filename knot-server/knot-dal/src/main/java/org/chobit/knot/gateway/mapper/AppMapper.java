package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.AppEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AppMapper {
    @Select("select id, app_id, name, owner_user_id, status, cast(rate_limit_json as char) as rate_limit_json, cast(quota_json as char) as quota_json from apps order by id desc")
    List<AppEntity> list();

    @Select("select id, app_id, name, owner_user_id, status, cast(rate_limit_json as char) as rate_limit_json, cast(quota_json as char) as quota_json from apps where id=#{id}")
    AppEntity getById(Long id);

    @Insert("insert into apps(app_id,name,owner_user_id,status,rate_limit_json,quota_json) values(#{appId},#{name},#{ownerUserId},#{status},#{rateLimitJson},#{quotaJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AppEntity entity);

    @Update("update apps set name=#{name}, owner_user_id=#{ownerUserId}, status=#{status}, rate_limit_json=#{rateLimitJson}, quota_json=#{quotaJson} where id=#{id}")
    int update(AppEntity entity);

    @Select("select count(1) from gateway_requests where app_id=#{appId}")
    Long countRequestsByAppId(Long appId);

    @Select("select count(1) from gateway_requests where app_id=#{appId} and status='SUCCESS'")
    Long countSuccessByAppId(Long appId);

    @Select("select coalesce(sum(total_tokens),0) from gateway_requests where app_id=#{appId}")
    Long sumTokensByAppId(Long appId);
}
