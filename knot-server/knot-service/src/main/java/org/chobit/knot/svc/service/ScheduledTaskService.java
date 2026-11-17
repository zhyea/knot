package org.chobit.knot.svc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.dal.mapper.ScheduledTaskMapper;
import org.chobit.knot.svc.converter.ScheduledTaskConverter;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务的数据访问：{@code ks_scheduled_tasks}（定义）+ {@code ks_scheduled_task_runs}（执行流水）。
 * {@link #finishRun} 只写结果字段，{@link #updateTaskFireTime} / {@link #updateTaskNextFireTime} 只写时间字段，不走整体更新；
 * {@link #acquireLease} / {@link #releaseLease} 用 {@code ks_scheduled_tasks} 的 {@code lock_owner} / {@code lease_until} 两列做执行租约。
 * 本类与 {@link AsyncTaskService} 是模块内碰 Mapper 的入口；调度基础设施（Quartz、handler 注册、部署节拍）留在 {@code knot-admin}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final ScheduledTaskMapper mapper;

    // ------------------------------------------------------------------ 任务定义

    /** 分页查询在调用侧（PageHelper），这里只返回当前页数据 */
    public List<ScheduledTaskDto> listTasks(String keyword, Integer status, String handlerCode) {
        return mapper.listTasks(keyword, status, handlerCode).stream()
                .map(ScheduledTaskConverter::toTaskRecord)
                .toList();
    }

    /** 启用中的全部任务（启动时装载调度用，不分页） */
    public List<ScheduledTaskDto> listEnabledTasks() {
        return mapper.listEnabledTasks().stream()
                .map(ScheduledTaskConverter::toTaskRecord)
                .toList();
    }

    public ScheduledTaskDto getTaskById(Long id) {
        return ScheduledTaskConverter.toTaskRecord(mapper.getTaskById(id));
    }

    public ScheduledTaskDto getTaskByCode(String taskCode) {
        return ScheduledTaskConverter.toTaskRecord(mapper.getTaskByCode(taskCode));
    }

    /**
     * 插入一条任务定义。
     * @return 新主键；不是靠「写回入参」——record 不可变，MyBatis 的
     * {@code useGeneratedKeys} 填不进去，故由实现内部转成实体后取回。
     */
    public Long insertTask(ScheduledTaskDto task) {
        // ⚠ 用 var 承接实体（不 import 它）：本类持有 Mapper 已经破例，
        // 再 import 实体就会让「只有 Converter 碰实体」这条红线失效。
        // 用实体承接自增主键是因为 record 不可变，useGeneratedKeys 写不进去——
        // 所以插入后从实体取回 id，而不是返回入参里的 id（那时还是 null）。
        var entity = ScheduledTaskConverter.toTaskEntity(task);
        mapper.insertTask(entity);
        return entity.getId();
    }

    public void updateTask(ScheduledTaskDto task) {
        mapper.updateTask(ScheduledTaskConverter.toTaskEntity(task));
    }

    /** 仅更新「下一次触发时间」（调度装载时用） */
    public void updateTaskNextFireTime(Long id, LocalDateTime nextFireAt) {
        mapper.updateTaskNextFireTime(id, nextFireAt);
    }

    /**
     * 执行结束后同时写入「上次触发」与「下次触发」，两个时间必须在同一条 UPDATE 里写。
     *
     * @param nextFireAt 可为空（手动触发时没有「下一拍」）
     */
    @Transactional
    public void updateTaskFireTime(Long id, LocalDateTime lastFireAt, LocalDateTime nextFireAt) {
        mapper.updateTaskFireTime(id, lastFireAt, nextFireAt);
    }

    // ------------------------------------------------------------------ 执行租约

    /**
     * 执行租约时长（分钟）。单个 handler 的墙钟耗时不得超过它，否则租约过期后会被别的节点抢跑；
     * 本类不做续租，需要更长的任务时再补心跳。
     */
    public static final int LEASE_MINUTES = 30;

    /**
     * 抢占任务执行租约。
     *
     * @param taskCode 任务编码
     * @param owner    节点 id；只有它自己能把租约放掉
     * @return 是否抢到；抢不到表示别的节点还在租期内，本次应当跳过
     */
    public boolean acquireLease(String taskCode, String owner) {
        return mapper.acquireLease(taskCode, owner, LEASE_MINUTES) == 1;
    }

    /** 释放自己的执行租约；失败吞掉（租约到期后会自动变成可抢，不该因此让任务执行失败） */
    public void releaseLease(String taskCode, String owner) {
        try {
            mapper.releaseLease(taskCode, owner);
        } catch (Exception e) {
            // 租约到期即自动可抢；释放失败不该让任务执行被判成失败
            log.warn("Failed to release scheduled task lease: taskCode={}, owner={}", taskCode, owner, e);
        }
    }

    // ------------------------------------------------------------------ 执行流水

    /** 分页查询在调用侧（PageHelper），这里只返回当前页数据 */
    public List<ScheduledTaskRunDto> listRuns(String taskCode, Integer status, String triggerType) {
        return mapper.listRuns(taskCode, status, triggerType).stream()
                .map(ScheduledTaskConverter::toRunRecord)
                .toList();
    }

    /**
     * 落一条「执行中」的记录，新主键靠返回值取回，不写回入参。
     */
    @Transactional
    public Long insertRun(ScheduledTaskRunDto run) {
        var entity = ScheduledTaskConverter.toRunEntity(run);
        mapper.insertRun(entity);
        return entity.getId();
    }

    /**
     * 收尾：只写结果字段，入参只带结果、不传整条记录。
     *
     * @param status       终态（{@code ScheduledTaskRunStatusEnum.code()}）
     * @param durationMs   耗时毫秒
     * @param affectedRows 影响行数
     * @param errorMsg     失败原因；成功时传 {@code null}（成功记录不该带着错误消息）
     */
    @Transactional
    public void finishRun(Long id, Integer status, LocalDateTime endTime, Long durationMs,
                          Integer affectedRows, String errorMsg) {
        // 实体的构造在 Converter 里，本类不出现实体类型名（持有 Mapper 已经是唯一破例）
        mapper.updateRun(ScheduledTaskConverter.newRunForFinish(
                id, status, endTime, durationMs, affectedRows, errorMsg));
    }

    /** 清理保留窗口之前的记录 */
    public int deleteRunsBefore(LocalDateTime beforeTime) {
        return mapper.deleteRunsBefore(beforeTime);
    }
}
