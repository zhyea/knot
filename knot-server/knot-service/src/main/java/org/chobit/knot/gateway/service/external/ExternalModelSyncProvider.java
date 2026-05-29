package org.chobit.knot.gateway.service.external;

import org.chobit.knot.gateway.dto.model.ExternalModelSyncResult;
import org.chobit.knot.gateway.entity.ExternalModelItemEntity;

public interface ExternalModelSyncProvider {

    String sourceCode();

    ExternalModelSyncResult sync();

    default ExternalModelItemEntity enrichDetail(ExternalModelItemEntity item) {
        return item;
    }
}
