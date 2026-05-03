package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.ModelEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ModelMapper {
    @Select("select id, provider_id, model_code, name, model_type, version, status, cast(rate_limit_json as char) as rate_limit_json, cast(quota_json as char) as quota_json from models order by id desc")
    List<ModelEntity> list();

    @Select("select id, provider_id, model_code, name, model_type, version, status, cast(rate_limit_json as char) as rate_limit_json, cast(quota_json as char) as quota_json from models where id=#{id}")
    ModelEntity getById(Long id);

    @Insert("insert into models(provider_id,model_code,name,model_type,version,status,rate_limit_json,quota_json) values(#{providerId},#{modelCode},#{name},#{modelType},#{version},#{status},#{rateLimitJson},#{quotaJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModelEntity entity);

    @Update("update models set provider_id=#{providerId}, name=#{name}, model_type=#{modelType}, version=#{version}, status=#{status}, rate_limit_json=#{rateLimitJson}, quota_json=#{quotaJson} where id=#{id}")
    int update(ModelEntity entity);
}
