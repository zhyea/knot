package com.knot.gateway.mapper;

import com.knot.gateway.entity.ModelVersionEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ModelVersionMapper {
    @Select("select id, model_id, version, gray_percent, status from model_versions where model_id=#{modelId} order by id desc")
    List<ModelVersionEntity> listByModelId(Long modelId);

    @Select("select id, model_id, version, gray_percent, status from model_versions where model_id=#{modelId} and status='ACTIVE' order by id desc limit 1")
    ModelVersionEntity getActiveVersion(Long modelId);

    @Insert("insert into model_versions(model_id, version, gray_percent, status) values(#{modelId}, #{version}, #{grayPercent}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModelVersionEntity entity);

    @Update("update model_versions set status=#{status} where id=#{id}")
    int updateStatus(ModelVersionEntity entity);
}
