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
 * 本类依赖 Quartz 的 {@link Scheduler}，数据访问经 {@link ScheduledTaskService}，只负责调度语义。
 */
@Slf4j
@Service
public class ScheduledTaskSchedulerService {

    private static final String GROUP = "knot-scheduled-tasks";

    private final Scheduler scheduler;
    private final ScheduledTaskService taskService;

    public ScheduledTaskSchedulerService(Scheduler scheduler, ScheduledTaskService taskService) {
        this.scheduler = scheduler;
        this.taskService = taskService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleEnabledTasks() {
        taskService.listEnabledTasks().forEach(this::reschedule);
    }

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

    public void unschedule(String taskCode) {
        try {
            scheduler.deleteJob(jobKey(taskCode));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to unschedule task: " + taskCode, e);
        }
    }

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
