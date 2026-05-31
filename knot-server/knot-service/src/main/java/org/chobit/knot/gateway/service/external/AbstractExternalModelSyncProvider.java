package org.chobit.knot.gateway.service.external;

import org.chobit.knot.gateway.dto.model.ExternalModelSyncResult;
import org.chobit.knot.gateway.entity.ExternalModelItemEntity;
import org.chobit.knot.gateway.entity.ExternalModelSourceEntity;
import org.chobit.knot.gateway.mapper.ExternalModelMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Executes the public operation. Executes the public operation.
 */
public abstract class AbstractExternalModelSyncProvider implements ExternalModelSyncProvider {

    protected static final String STATUS_SYNCED = "SYNCED";

    private final ExternalModelMapper externalModelMapper;

    protected AbstractExternalModelSyncProvider(ExternalModelMapper externalModelMapper) {
        this.externalModelMapper = externalModelMapper;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public ExternalModelSyncResult sync() {
        ExternalModelSourceEntity source = ensureSource();
        List<ExternalModelItemEntity> items = fetchItems(source);
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ExternalModelItemEntity item : items) {
            try {
                item.setSourceCode(sourceCode());
                item.setSyncStatus(STATUS_SYNCED);
                item.setLastSeenAt(now);
                ExternalModelItemEntity existing =
                        externalModelMapper.getItemBySourceKey(item.getSourceCode(), item.getModelId());
                if (existing == null) {
                    externalModelMapper.insertItem(item);
                    inserted++;
                } else if (item.getSyncHash() != null && item.getSyncHash().equals(existing.getSyncHash())) {
                    existing.setLastSeenAt(now);
                    existing.setSyncStatus(STATUS_SYNCED);
                    externalModelMapper.updateItem(copyUpdatable(existing, item));
                    skipped++;
                } else {
                    item.setId(existing.getId());
                    item.setLogicalModelId(existing.getLogicalModelId());
                    externalModelMapper.updateItem(item);
                    updated++;
                }
            } catch (Exception e) {
                failed++;
            }
        }
        externalModelMapper.updateSourceLastSyncAt(sourceCode(), now);
        return new ExternalModelSyncResult(
                items.size(), inserted, updated, skipped, failed,
                "source=" + sourceCode() + ", total=" + items.size() +
                        ", inserted=" + inserted + ", updated=" + updated +
                        ", skipped=" + skipped + ", failed=" + failed
        );
    }

    protected abstract ExternalModelSourceEntity defaultSource();

    protected abstract List<ExternalModelItemEntity> fetchItems(ExternalModelSourceEntity source);

    private ExternalModelSourceEntity ensureSource() {
        ExternalModelSourceEntity source = externalModelMapper.getSourceByCode(sourceCode());
        if (source != null) {
            return source;
        }
        source = defaultSource();
        externalModelMapper.insertSource(source);
        return source;
    }

    private ExternalModelItemEntity copyUpdatable(ExternalModelItemEntity target, ExternalModelItemEntity source) {
        source.setId(target.getId());
        source.setLogicalModelId(target.getLogicalModelId());
        return source;
    }
}
