package org.chobit.knot.gateway.service.schedule;

import org.chobit.knot.gateway.dto.model.ExternalModelSyncResult;
import org.chobit.knot.gateway.service.ExternalModelService;
import org.chobit.knot.gateway.service.external.OpenRouterModelSyncProvider;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterModelSyncHandler implements ScheduledTaskHandler {

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static final String CODE = "OPENROUTER_MODEL_SYNC";

    private final ExternalModelService externalModelService;

    /**
     * Constructs a new instance.
     */
    public OpenRouterModelSyncHandler(ExternalModelService externalModelService) {
        this.externalModelService = externalModelService;
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @Override
    public String handlerCode() {
        return CODE;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public ScheduledTaskResult execute() {
        ExternalModelSyncResult result = externalModelService.sync(OpenRouterModelSyncProvider.CODE);
        return new ScheduledTaskResult(result.affectedRows(), result.message());
    }
}
