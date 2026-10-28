package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;

import java.util.List;

/**
 * 异步任务查询口径档案读写（{@code kb_async_task_profiles}）。
 */
public interface AsyncTaskProfileMapper {

    AsyncTaskProfileEntity getByProfileCode(@Param("profileCode") String profileCode);

    List<AsyncTaskProfileEntity> listEnabled();

    int insert(AsyncTaskProfileEntity entity);

    int update(AsyncTaskProfileEntity entity);

    int deleteByProfileCode(@Param("profileCode") String profileCode);
}
