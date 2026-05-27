package org.chobit.knot.gateway.service.schedule;

public interface ScheduledTaskHandler {

    String handlerCode();

    ScheduledTaskResult execute();
}
