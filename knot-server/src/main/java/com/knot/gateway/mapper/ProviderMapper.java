package com.knot.gateway.mapper;

import com.knot.gateway.entity.ProviderEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProviderMapper {
    @Select("select id, code, name, provider_type, base_url, status, cast(rate_limit_json as char) as rate_limit_json, cast(quota_json as char) as quota_json from providers order by id desc")
    List<ProviderEntity> list();

    @Select("select id, code, name, provider_type, base_url, status, cast(rate_limit_json as char) as rate_limit_json, cast(quota_json as char) as quota_json from providers where id = #{id}")
    ProviderEntity getById(Long id);

    @Insert("insert into providers(code,name,provider_type,base_url,status,rate_limit_json,quota_json) values(#{code},#{name},#{providerType},#{baseUrl},#{status},#{rateLimitJson},#{quotaJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProviderEntity entity);

    @Update("update providers set name=#{name}, provider_type=#{providerType}, base_url=#{baseUrl}, status=#{status}, rate_limit_json=#{rateLimitJson}, quota_json=#{quotaJson} where id=#{id}")
    int update(ProviderEntity entity);
}
