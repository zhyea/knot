package org.chobit.knot.gateway.service.schedule;

import org.chobit.knot.gateway.dto.model.ExternalModelSyncResult;
import org.chobit.knot.gateway.service.ExternalModelService;
import org.chobit.knot.gateway.service.external.AiBaseCommercialLlmSyncProvider;
import org.springframework.stereotype.Component;

@Component
public class AiBaseCommercialModelSyncHandler implements ScheduledTaskHandler {

    public static final String CODE = "AIBASE_COMMERCIAL_MODEL_SYNC";

    private final ExternalModelService externalModelService;

    public AiBaseCommercialModelSyncHandler(ExternalModelService externalModelService) {
        this.externalModelService = externalModelService;
    }

    @Override
    public String handlerCode() {
        return CODE;
    }

    @Override
    public ScheduledTaskResult execute() {
        ExternalModelSyncResult result = externalModelService.sync(AiBaseCommercialLlmSyncProvider.CODE);
        return new ScheduledTaskResult(result.affectedRows(), result.message());
    }
}
