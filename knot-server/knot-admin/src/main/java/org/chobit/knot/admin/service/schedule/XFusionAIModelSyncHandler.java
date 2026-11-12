package org.chobit.knot.admin.service.schedule;

import org.chobit.knot.svc.dto.model.ExternalModelSyncResult;
import org.chobit.knot.svc.service.ExternalModelService;
import org.chobit.knot.svc.service.external.XFusionAIModelSyncProvider;
import org.springframework.stereotype.Component;

@Component
public class XFusionAIModelSyncHandler implements ScheduledTaskHandler {

    public static final String CODE = "XFUSIONAI_MODEL_SYNC";

    private final ExternalModelService externalModelService;

    /**
     * Constructs a new instance.
     */
    public XFusionAIModelSyncHandler(ExternalModelService externalModelService) {
        this.externalModelService = externalModelService;
    }

    @Override
    public String handlerCode() {
        return CODE;
    }

    @Override
    public ScheduledTaskResult execute() {
        ExternalModelSyncResult result = externalModelService.sync(XFusionAIModelSyncProvider.CODE);
        return new ScheduledTaskResult(result.affectedRows(), result.message());
    }
}
