package org.chobit.knot.svc.service.external;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.constants.enums.ExternalModelSyncStatusEnum;
import org.chobit.knot.svc.dto.model.ExternalModelSyncResult;
import org.chobit.knot.dal.entity.ExternalModelItemEntity;
import org.chobit.knot.dal.entity.ExternalModelSourceEntity;
import org.chobit.knot.dal.mapper.ExternalModelMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractExternalModelSyncProvider implements ExternalModelSyncProvider {

    private final ExternalModelMapper externalModelMapper;

    protected AbstractExternalModelSyncProvider(ExternalModelMapper externalModelMapper) {
        this.externalModelMapper = externalModelMapper;
    }

    @Override
    public ExternalModelSyncResult sync() {
        ExternalModelSourceEntity source = ensureSource();
        List<ExternalModelItemEntity> items = fetchItems(source);
        // 一次按 model_id 集合查回已有条目：逐条 getItemByModelId 会让 N 个模型变成 2N 次往返
        Map<String, ExternalModelItemEntity> existingByModelId = loadExisting(items);
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<ExternalModelSyncResult.ItemFailure> failures = new ArrayList<>();
        boolean failuresTruncated = false;
        LocalDateTime now = LocalDateTime.now();
        for (ExternalModelItemEntity item : items) {
            ExternalModelItemEntity existing = null;
            try {
                item.setSourceCode(sourceCode());
                item.setSyncStatus(ExternalModelSyncStatusEnum.SYNCED.code());
                // 去重只认 model_id（2026-11-01 robin 拍板）：同一模型跨源视为同一条目，
                // 后同步的源覆盖旧数据，条目归属（source_code）随 updateItem 翻到新源。
                existing = existingByModelId.get(item.getModelId());
                if (existing == null) {
                    externalModelMapper.insertItem(item);
                    inserted++;
                } else if (item.getSyncHash() != null && item.getSyncHash().equals(existing.getSyncHash())) {
                    existing.setSyncStatus(ExternalModelSyncStatusEnum.SYNCED.code());
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
                log.warn("External model sync failed: source={}, modelId={}", sourceCode(), item.getModelId(), e);
                if (failures.size() < ExternalModelSyncResult.MAX_FAILURES) {
                    failures.add(new ExternalModelSyncResult.ItemFailure(
                            item.getId() != null ? String.valueOf(item.getId()) : null,
                            item.getModelId(),
                            e.getMessage() != null && !e.getMessage().isBlank()
                                    ? e.getMessage() : e.getClass().getSimpleName()));
                } else {
                    failuresTruncated = true;
                }
                // 单条失败必须落 FAILED(3)，否则记录永远停在 PENDING(1)，页面上看不出同步出过错
                if (existing != null && existing.getId() != null) {
                    existing.setSyncStatus(ExternalModelSyncStatusEnum.FAILED.code());
                    externalModelMapper.updateItem(existing);
                }
            }
        }
        externalModelMapper.updateSourceLastSyncAt(sourceCode(), now);
        return new ExternalModelSyncResult(
                items.size(), inserted, updated, skipped, failed,
                "source=" + sourceCode() + ", total=" + items.size() +
                        ", inserted=" + inserted + ", updated=" + updated +
                        ", skipped=" + skipped + ", failed=" + failed,
                failures, failuresTruncated
        );
    }

    /**
     * 按 {@code model_id} 集合批量取回已有条目，键为 {@code model_id}；空集合不查库。
     */
    private Map<String, ExternalModelItemEntity> loadExisting(List<ExternalModelItemEntity> items) {
        List<String> modelIds = items.stream()
                .map(ExternalModelItemEntity::getModelId)
                .filter(modelId -> modelId != null && !modelId.isBlank())
                .distinct()
                .toList();
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        return externalModelMapper.listItemsByModelIds(modelIds).stream()
                .collect(Collectors.toMap(ExternalModelItemEntity::getModelId, Function.identity(), (a, b) -> a));
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
