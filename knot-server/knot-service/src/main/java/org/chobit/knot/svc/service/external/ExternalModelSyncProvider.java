package org.chobit.knot.svc.service.external;

import org.chobit.knot.svc.dto.model.ExternalModelSyncResult;
import org.chobit.knot.dal.entity.ExternalModelItemEntity;

public interface ExternalModelSyncProvider {

    String sourceCode();

    ExternalModelSyncResult sync();

    default ExternalModelItemEntity enrichDetail(ExternalModelItemEntity item) {
        return item;
    }
}
