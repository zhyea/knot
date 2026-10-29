package org.chobit.knot.admin.service.schedule;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.service.ScheduledTaskService;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 把任务定义装进 Quartz，并在触发后回写「下一次触发时间」。
 *
 * <p>⭐ <b>为什么留在 admin</b>：本类依赖 Quartz 的 {@link Scheduler}。
 * knot-service 是 admin 与 gateway 共用的层，把 Quartz 装配塞进去会让网关也去装配一个
 * 它根本不用的调度器（网关没有 Quartz 配置，缺 bean 会直接起不来）。
 * 数据访问已收进 svc 的 {@link ScheduledTaskService}，本类只负责调度语义。</p>
 */
@Slf4j
@Service
public class ScheduledTaskSchedulerService {

    private static final String GROUP = "knot-scheduled-tasks";

    private final Scheduler scheduler;
    private final ScheduledTaskService taskService;

    /**
     * Constructs a new instance.
     */
    public ScheduledTaskSchedulerService(Scheduler scheduler, ScheduledTaskService taskService) {
        this.scheduler = scheduler;
        this.taskService = taskService;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleEnabledTasks() {
        taskService.listEnabledTasks().forEach(this::reschedule);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void reschedule(ScheduledTaskDto task) {
        try {
            unschedule(task.taskCode());
            if (!EnabledStatusEnum.isEnabled(task.status())) {
                return;
            }

            JobDataMap dataMap = new JobDataMap();
            dataMap.put(ScheduledTaskQuartzJob.DATA_TASK_CODE, task.taskCode());
            dataMap.put(ScheduledTaskQuartzJob.DATA_TRIGGER_TYPE, ScheduledTaskQuartzJob.TRIGGER_SCHEDULE);

            JobDetail jobDetail = JobBuilder.newJob(ScheduledTaskQuartzJob.class)
                    .withIdentity(jobKey(task.taskCode()))
                    .usingJobData(dataMap)
                    .storeDurably()
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(task.taskCode()))
                    .forJob(jobDetail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            updateNextFireTime(task.id(), trigger.getNextFireTime());
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to schedule task: " + task.taskCode(), e);
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void unschedule(String taskCode) {
        try {
            scheduler.deleteJob(jobKey(taskCode));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to unschedule task: " + taskCode, e);
        }
    }

    /**
     * Triggers the requested operation immediately. Executes the public operation.
     */
    public void triggerNow(String taskCode) {
        try {
            ScheduledTaskDto task = taskService.getTaskByCode(taskCode);
            if (task == null || !EnabledStatusEnum.isEnabled(task.status())) {
                throw new IllegalArgumentException("Task is not enabled: " + taskCode);
            }
            JobKey jobKey = jobKey(taskCode);
            if (!scheduler.checkExists(jobKey)) {
                reschedule(task);
            }
            JobDataMap dataMap = new JobDataMap();
            dataMap.put(ScheduledTaskQuartzJob.DATA_TRIGGER_TYPE, ScheduledTaskQuartzJob.TRIGGER_MANUAL);
            scheduler.triggerJob(jobKey, dataMap);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to trigger task: " + taskCode, e);
        }
    }

    private JobKey jobKey(String taskCode) {
        return JobKey.jobKey(taskCode, GROUP);
    }

    private TriggerKey triggerKey(String taskCode) {
        return TriggerKey.triggerKey(taskCode, GROUP);
    }

    private void updateNextFireTime(Long taskId, Date nextFireTime) {
        LocalDateTime next = nextFireTime == null
                ? null
                : LocalDateTime.ofInstant(nextFireTime.toInstant(), ZoneId.systemDefault());
        taskService.updateTaskNextFireTime(taskId, next);
    }
}
