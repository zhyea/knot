package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ScheduledTaskEntity;
import org.chobit.knot.gateway.entity.ScheduledTaskRunEntity;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ScheduledTaskMapper {

    List<ScheduledTaskEntity> listTasks(@Param("taskCode") String taskCode,
                                        @Param("status") String status,
                                        @Param("handlerCode") String handlerCode);

    List<ScheduledTaskEntity> listEnabledTasks();

    ScheduledTaskEntity getTaskById(Long id);

    ScheduledTaskEntity getTaskByCode(String taskCode);

    int insertTask(ScheduledTaskEntity entity);

    int updateTask(ScheduledTaskEntity entity);

    int updateTaskFireTime(@Param("id") Long id,
                           @Param("lastFireAt") LocalDateTime lastFireAt,
                           @Param("nextFireAt") LocalDateTime nextFireAt);

    int updateTaskNextFireTime(@Param("id") Long id,
                               @Param("nextFireAt") LocalDateTime nextFireAt);

    int insertRun(ScheduledTaskRunEntity entity);

    int updateRun(ScheduledTaskRunEntity entity);

    List<ScheduledTaskRunEntity> listRuns(@Param("taskCode") String taskCode,
                                          @Param("status") String status,
                                          @Param("triggerType") String triggerType);

    int deleteRunsBefore(LocalDateTime beforeTime);

    Integer acquireLock(@Param("lockName") String lockName);

    Integer releaseLock(@Param("lockName") String lockName);
}
