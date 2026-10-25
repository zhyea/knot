package org.chobit.knot.admin.service.schedule;

public interface ScheduledTaskHandler {

    String handlerCode();

    ScheduledTaskResult execute();
}
