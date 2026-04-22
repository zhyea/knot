package com.knot.gateway.mapper;

import com.knot.gateway.entity.GrayPlanEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GrayPlanMapper {
    @Insert("insert into gray_plans(plan_name,target_type,target_id,traffic_percent,steps_json,start_time,status) "
            + "values(#{planName},#{targetType},#{targetId},#{trafficPercent},#{stepsJson},#{startTime},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GrayPlanEntity entity);

    @Select("select id,plan_name,target_type,target_id,traffic_percent,cast(steps_json as char) as steps_json,start_time,status from gray_plans order by id desc")
    List<GrayPlanEntity> list();

    @Select("select id,plan_name,target_type,target_id,traffic_percent,cast(steps_json as char) as steps_json,start_time,status from gray_plans where id=#{id}")
    GrayPlanEntity getById(Long id);

    @Update("update gray_plans set status=#{status} where id=#{id}")
    int updateStatus(GrayPlanEntity entity);
}
