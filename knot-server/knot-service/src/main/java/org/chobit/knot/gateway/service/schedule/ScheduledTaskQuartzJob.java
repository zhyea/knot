package org.chobit.knot.gateway.service.schedule;

import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

public class ScheduledTaskQuartzJob extends QuartzJobBean {

    public static final String DATA_TASK_CODE = "taskCode";
    public static final String DATA_TRIGGER_TYPE = "triggerType";
    public static final String TRIGGER_SCHEDULE = "SCHEDULE";
    public static final String TRIGGER_MANUAL = "MANUAL";

    @Autowired
    private ScheduledTaskExecutor executor;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        String taskCode = context.getMergedJobDataMap().getString(DATA_TASK_CODE);
        String triggerType = context.getMergedJobDataMap().getString(DATA_TRIGGER_TYPE);
        executor.execute(taskCode, triggerType != null ? triggerType : TRIGGER_SCHEDULE, context.getTrigger().getNextFireTime());
    }
}
