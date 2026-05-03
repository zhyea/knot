package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.GrayPlanEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface GrayPlanMapper {

    int insert(GrayPlanEntity entity);

    List<GrayPlanEntity> list();

    GrayPlanEntity getById(Long id);

    int updateStatus(GrayPlanEntity entity);
}
