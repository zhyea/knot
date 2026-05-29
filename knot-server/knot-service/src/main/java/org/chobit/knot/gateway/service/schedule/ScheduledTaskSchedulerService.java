package org.chobit.knot.gateway.service.schedule;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.constants.EntityStatus;
import org.chobit.knot.gateway.entity.ScheduledTaskEntity;
import org.chobit.knot.gateway.mapper.ScheduledTaskMapper;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
public class ScheduledTaskSchedulerService {

    private static final String GROUP = "knot-scheduled-tasks";

    private final Scheduler scheduler;
    private final ScheduledTaskMapper scheduledTaskMapper;

    public ScheduledTaskSchedulerService(Scheduler scheduler, ScheduledTaskMapper scheduledTaskMapper) {
        this.scheduler = scheduler;
        this.scheduledTaskMapper = scheduledTaskMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleEnabledTasks() {
        scheduledTaskMapper.listEnabledTasks().forEach(this::reschedule);
    }

    public void reschedule(ScheduledTaskEntity task) {
        try {
            unschedule(task.getTaskCode());
            if (!EntityStatus.ENABLED.equals(task.getStatus())) {
                return;
            }

            JobDataMap dataMap = new JobDataMap();
            dataMap.put(ScheduledTaskQuartzJob.DATA_TASK_CODE, task.getTaskCode());
            dataMap.put(ScheduledTaskQuartzJob.DATA_TRIGGER_TYPE, ScheduledTaskQuartzJob.TRIGGER_SCHEDULE);

            JobDetail jobDetail = JobBuilder.newJob(ScheduledTaskQuartzJob.class)
                    .withIdentity(jobKey(task.getTaskCode()))
                    .usingJobData(dataMap)
                    .storeDurably()
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(task.getTaskCode()))
                    .forJob(jobDetail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            updateNextFireTime(task.getId(), trigger.getNextFireTime());
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to schedule task: " + task.getTaskCode(), e);
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
            ScheduledTaskEntity task = scheduledTaskMapper.getTaskByCode(taskCode);
            if (task == null || !EntityStatus.ENABLED.equals(task.getStatus())) {
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
        scheduledTaskMapper.updateTaskNextFireTime(taskId, next);
    }
}
