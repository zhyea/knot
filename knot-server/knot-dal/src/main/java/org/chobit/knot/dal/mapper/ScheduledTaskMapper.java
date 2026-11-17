package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.ScheduledTaskEntity;
import org.chobit.knot.dal.entity.ScheduledTaskRunEntity;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ScheduledTaskMapper {

    List<ScheduledTaskEntity> listTasks(@Param("keyword") String keyword,
                                        @Param("status") Integer status,
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
                                          @Param("status") Integer status,
                                          @Param("triggerType") String triggerType);

    int deleteRunsBefore(LocalDateTime beforeTime);

    /**
     * 抢占任务执行租约：影响行数 = 1 才算抢到，0 表示别的节点还在租期内。
     *
     * @param taskCode     任务编码
     * @param owner        抢租约的节点 id，写进 {@code lock_owner}；只有它能释放
     * @param leaseMinutes 租期分钟数，从数据库当前时刻起算
     */
    int acquireLease(@Param("taskCode") String taskCode,
                     @Param("owner") String owner,
                     @Param("leaseMinutes") int leaseMinutes);

    /**
     * 释放任务执行租约：只清自己的（{@code lock_owner = owner}），不会误清已被别人抢走的租约。
     */
    int releaseLease(@Param("taskCode") String taskCode, @Param("owner") String owner);
}
