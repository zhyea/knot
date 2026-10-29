package org.chobit.knot.svc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.dal.mapper.ScheduledTaskMapper;
import org.chobit.knot.svc.converter.ScheduledTaskConverter;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务的数据访问：{@code ks_scheduled_tasks}（定义）+ {@code ks_scheduled_task_runs}（执行流水）。
 *
 * <h3>⭐ 为什么一个类，不是两个 Store</h3>
 * <p>初版拆成 {@code ScheduledTaskStore} + {@code ScheduledTaskRunStore}，理由是「两张表、两种生命周期」。
 * 拆完的事实是：<b>两个实现各自注入同一个 {@code ScheduledTaskMapper}</b>（两张表在同一个 mapper 文件里），
 * 两个接口都没有第二个实现——那就是把「一个 Mapper 上的方法」按表切成两个类型，
 * 顺带多出四个文件。判据是<b>单实现接口 = 纯转发壳</b>；要表达的边界用方法名与方法注释就够，
 * 不需要多造一个类型。</p>
 *
 * <p>⚠ 「两张表」这条边界确实存在，它体现在方法上：{@link #finishRun} 只写结果字段、
 * {@link #updateTaskFireTime} 只写两个时间字段——用<b>入参收窄</b>守边界，比用类型守更准也更省。</p>
 *
 * <h3>⭐ 锁为什么在这里</h3>
 * <p>{@link #acquireLock} / {@link #releaseLock} 用的是 MySQL 的 {@code GET_LOCK}，与任务互斥这件事
 * 绑在一起，而不是通用分布式锁。⚠ 不要抽成通用 {@code LockXxx}：那会诱导别处用它做无关互斥，
 * 而这些锁是<b>会话级</b>的——连接一断就没了，换个用途就是假的互斥。</p>
 *
 * <p>⚠ <b>nextFireAt 的写入与任务的读写分开</b>（{@link #updateTaskNextFireTime} /
 * {@link #updateTaskFireTime}），不走整体更新：调度与执行各自只改时间字段，走整体更新会把
 * 「上次触发」与「下次触发」的写入混进一次全字段覆盖，两个线程各写各的字段时互相抹掉。</p>
 *
 * <p>本类与 {@link AsyncTaskService} 是模块内碰 Mapper 的入口之一（另加两个 Converter 碰实体）；
 * 调度基础设施（Quartz、handler 注册、部署节拍）留在 {@code knot-admin}，不进本类。</p>
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
     *
     * @return 新主键；⚠ 不是靠「写回入参」——record 不可变，MyBatis 的
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
     * 执行结束后同时写入「上次触发」与「下次触发」。
     *
     * <p>⚠ 两个时间必须在<b>同一条 UPDATE</b> 里写：分两次会让中间态被读到，
     * 表现为「下次触发时间已经翻页、上次触发时间还是旧的」。</p>
     *
     * @param nextFireAt 可为空（手动触发时没有「下一拍」）
     */
    public void updateTaskFireTime(Long id, LocalDateTime lastFireAt, LocalDateTime nextFireAt) {
        mapper.updateTaskFireTime(id, lastFireAt, nextFireAt);
    }

    // ------------------------------------------------------------------ 执行锁

    /**
     * 抢占执行锁。
     *
     * @return 是否抢到；抢不到表示别的节点正在跑，本次应当跳过
     */
    public boolean acquireLock(String lockName) {
        Integer acquired = mapper.acquireLock(lockName);
        return acquired != null && acquired == 1;
    }

    /** 释放执行锁；失败吞掉（锁会随连接断开自动释放，不该因此让任务执行失败） */
    public void releaseLock(String lockName) {
        try {
            mapper.releaseLock(lockName);
        } catch (Exception e) {
            // 锁是会话级的，连接断开会自动释放；释放失败不该让任务执行被判成失败
            log.warn("Failed to release scheduled task lock: {}", lockName, e);
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
     * 落一条「执行中」的记录。
     *
     * <p>与 {@link #insertTask} 同理：靠返回值而不是写回入参（record 不可变）。</p>
     */
    public Long insertRun(ScheduledTaskRunDto run) {
        var entity = ScheduledTaskConverter.toRunEntity(run);
        mapper.insertRun(entity);
        return entity.getId();
    }

    /**
     * 收尾：只写结果字段。
     *
     * <p>⚠ <b>不传整个记录</b>：收尾只该写结果，若允许传整条记录，就等于允许顺手把
     * {@code taskCode} / {@code nodeId} 这些<b>执行开始时的事实</b>也改掉——一条记录会自相矛盾
     * （跑在 A 节点、记录说是 B 节点）。把入参收窄到「只有结果」，是用类型而不是靠自觉守这条线。</p>
     *
     * @param status       终态（{@code ScheduledTaskRunStatusEnum.code()}）
     * @param durationMs   耗时毫秒
     * @param affectedRows 影响行数
     * @param errorMsg     失败原因；成功时传 {@code null}（成功记录不该带着错误消息）
     */
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
