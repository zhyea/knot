package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.PluginEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PluginMapper {
    @Select("select id,code,name,plugin_type,version,status from plugins order by id desc")
    List<PluginEntity> list();

    @Select("select id,code,name,plugin_type,version,status from plugins where id=#{id}")
    PluginEntity getById(Long id);

    @Insert("insert into plugins(code,name,plugin_type,version,status) values(#{code},#{name},#{pluginType},#{version},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PluginEntity entity);

    @Update("update plugins set status=#{status} where id=#{id}")
    int updateStatus(PluginEntity entity);
}
